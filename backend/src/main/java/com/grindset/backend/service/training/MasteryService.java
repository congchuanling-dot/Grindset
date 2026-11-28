package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.config.TrainingProperties;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.UserDimensionMastery;
import com.grindset.backend.entity.UserKpMastery;
import com.grindset.backend.entity.UserKpMasteryLog;
import com.grindset.backend.mapper.UserDimensionMasteryMapper;
import com.grindset.backend.mapper.UserKpMasteryLogMapper;
import com.grindset.backend.mapper.UserKpMasteryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.Numbers.intValue;
import static com.grindset.backend.common.constant.MasteryRules.STALE_DAYS;

/**
 * 知识点 + 五维掌握度：面试与训练的统一观测入口。
 *
 * <p>本类只负责读写掌握度，事务边界由调用方控制：
 * 面试复盘在报告事务里调用，训练作答在计划项事务里调用。</p>
 */
@Service
public class MasteryService implements WeakPointProvider {

    private final UserKpMasteryMapper masteryMapper;
    private final UserKpMasteryLogMapper logMapper;
    private final UserDimensionMasteryMapper dimensionMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final TrainingProperties properties;

    public MasteryService(UserKpMasteryMapper masteryMapper,
                          UserKpMasteryLogMapper logMapper,
                          UserDimensionMasteryMapper dimensionMapper,
                          SnowflakeIdGenerator idGenerator,
                          TrainingProperties properties) {
        this.masteryMapper = masteryMapper;
        this.logMapper = logMapper;
        this.dimensionMapper = dimensionMapper;
        this.idGenerator = idGenerator;
        this.properties = properties;
    }

    public UserKpMastery find(long userId, String kpKey) {
        if (kpKey == null || kpKey.isBlank()) {
            return null;
        }
        return masteryMapper.selectOne(new QueryWrapper<UserKpMastery>()
                .eq("user_id", userId)
                .eq("kp_key", kpKey)
                .last("LIMIT 1"));
    }

    /** 面试结束：对每道已评分题的所有知识点各记一次观测 */
    public void observeInterviewQuestions(long userId, long sessionId, List<InterviewQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        double alpha = properties.getMastery().getEmaAlpha();
        for (InterviewQuestion question : questions) {
            if (question.getScore() == null || question.getKnowledgePoints() == null) {
                continue;
            }
            String dimension = TrainingDimensionResolver.of(question).name();
            for (String point : question.getKnowledgePoints()) {
                observe(userId, point, null, dimension, null, question.getScore(), alpha,
                        "INTERVIEW", sessionId, question.getId());
            }
        }
    }

    /** 训练作答：复测权重更高 */
    public MasteryChange observeTraining(long userId, long planId, long itemId,
                                         String kpKey, String kpName, String dimension,
                                         int score, boolean retest) {
        double alpha = retest
                ? properties.getMastery().getRetestAlpha()
                : properties.getMastery().getEmaAlpha();
        return observe(userId, kpName == null ? kpKey : kpName, kpKey, dimension, null,
                score, alpha, "TRAINING", planId, itemId);
    }

    /**
     * 观测一次得分。同一来源条目（同一道面试题 / 同一个训练题）对同一知识点只记一次，
     * 避免重复提交或复盘重试把掌握度算两遍。
     */
    public MasteryChange observe(long userId, String kpName, String explicitKey, String dimension,
                                 String domain, int score, double alpha, String sourceType,
                                 Long sourceId, Long sourceItemId) {
        String key = explicitKey != null && !explicitKey.isBlank()
                ? explicitKey : KnowledgePointKeys.keyOf(kpName);
        String name = KnowledgePointKeys.displayName(kpName);
        if (key.isEmpty() || name.isEmpty()) {
            return null;
        }
        String dim = dimension == null || dimension.isBlank()
                ? TrainingDimension.BASICS.name() : dimension;
        UserKpMastery existing = find(userId, key);
        int current = existing == null ? properties.getMastery().getInitial() : existing.getMastery();

        if (alreadyObserved(userId, key, sourceType, sourceId, sourceItemId)) {
            return new MasteryChange(key, name, dim, current, current, 0,
                    levelOf(current), existing == null ? null : existing.getNextReviewAt(),
                    existing != null && existing.getEliminatedAt() != null, false);
        }

        int observed = Math.max(0, Math.min(100, score * 20));
        double weight = Math.max(0, Math.min(1, alpha));
        int after = (int) Math.round(current * (1 - weight) + observed * weight);
        after = Math.max(0, Math.min(100, after));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReview = now.plusDays(reviewDays(score));

        UserKpMastery entity = existing == null ? new UserKpMastery() : existing;
        if (existing == null) {
            entity.setId(idGenerator.nextId());
            entity.setUserId(userId);
            entity.setKpKey(key);
            entity.setCreatedAt(now);
            entity.setEvidenceCount(0);
            entity.setConsecutiveGood(0);
        }
        int good = score >= 4 ? intValue(entity.getConsecutiveGood()) + 1 : 0;
        boolean eliminated = after >= 75 && good >= 2;
        entity.setKpName(name);
        entity.setDimension(dim);
        if (domain != null && !domain.isBlank()) {
            entity.setDomain(domain);
        }
        entity.setMastery(after);
        entity.setLevel(levelOf(after));
        entity.setEvidenceCount(intValue(entity.getEvidenceCount()) + 1);
        entity.setLastScore(score);
        entity.setLastSource(sourceType);
        entity.setLastBefore(current);
        entity.setLastDelta(after - current);
        entity.setConsecutiveGood(good);
        entity.setLastPracticedAt(now);
        entity.setNextReviewAt(nextReview);
        entity.setEliminatedAt(eliminated ? now : (score <= 2 ? null : entity.getEliminatedAt()));
        entity.setUpdatedAt(now);
        if (existing == null) {
            masteryMapper.insert(entity);
        } else {
            masteryMapper.updateById(entity);
        }

        UserKpMasteryLog log = new UserKpMasteryLog();
        log.setId(idGenerator.nextId());
        log.setUserId(userId);
        log.setKpKey(key);
        log.setDimension(dim);
        log.setBeforeScore(current);
        log.setAfterScore(after);
        log.setDelta(after - current);
        log.setScore(score);
        log.setSourceType(sourceType);
        log.setSourceId(sourceId);
        log.setSourceItemId(sourceItemId);
        log.setCreatedAt(now);
        logMapper.insert(log);

        recomputeDimension(userId, dim);
        return new MasteryChange(key, name, dim, current, after, after - current,
                levelOf(after), nextReview, eliminated, true);
    }

    @Override
    public List<String> topWeakNames(long userId, int limit) {
        return weakItems(userId, limit).stream()
                .filter(item -> !"MASTERED".equals(item.getLevel()))
                .map(UserKpMastery::getKpName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    /** 今日弱项：掌握度低、到期、陈旧的排在前面 */
    public List<UserKpMastery> weakItems(long userId, int limit) {
        int max = Math.max(1, Math.min(limit, 20));
        List<UserKpMastery> rows = masteryMapper.selectList(new QueryWrapper<UserKpMastery>()
                .eq("user_id", userId)
                .and(wrapper -> wrapper.lt("mastery", 75)
                        .or()
                        .le("next_review_at", LocalDateTime.now()))
                .orderByAsc("mastery")
                .last("LIMIT " + max));
        LocalDateTime now = LocalDateTime.now();
        List<UserKpMastery> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .comparingInt((UserKpMastery item) -> isDue(item, now) ? 0 : 1)
                .thenComparingInt(item -> intValue(item.getMastery()))
                .thenComparingLong(item -> -staleDays(item, now)));
        return sorted;
    }

    public int dueCount(long userId) {
        return Math.toIntExact(masteryMapper.selectCount(new QueryWrapper<UserKpMastery>()
                .eq("user_id", userId)
                .isNotNull("next_review_at")
                .le("next_review_at", LocalDateTime.now())));
    }

    public int weakCount(long userId) {
        return Math.toIntExact(masteryMapper.selectCount(new QueryWrapper<UserKpMastery>()
                .eq("user_id", userId)
                .lt("mastery", 40)));
    }

    /** 是否已有任何掌握度数据，用于判断是否需要先做基线测评 */
    public boolean hasData(long userId) {
        return masteryMapper.selectCount(new QueryWrapper<UserKpMastery>()
                .eq("user_id", userId)) > 0;
    }

    /** 最近几次作答得分，供自适应难度使用 */
    public List<Integer> recentScores(long userId, String kpKey, int limit) {
        List<UserKpMasteryLog> logs = logMapper.selectList(new QueryWrapper<UserKpMasteryLog>()
                .eq("user_id", userId)
                .eq("kp_key", kpKey)
                .isNotNull("score")
                .orderByDesc("created_at")
                .last("LIMIT " + Math.max(1, Math.min(limit, 10))));
        List<Integer> scores = new ArrayList<>(logs.size());
        for (UserKpMasteryLog log : logs) {
            scores.add(log.getScore());
        }
        java.util.Collections.reverse(scores);
        return scores;
    }

    /** 五维当前分；缺失维度用 50 兜底 */
    public Map<TrainingDimension, Integer> dimensionMastery(long userId) {
        Map<TrainingDimension, Integer> result = new EnumMap<>(TrainingDimension.class);
        for (UserDimensionMastery row : dimensionMapper.selectList(
                new QueryWrapper<UserDimensionMastery>().eq("user_id", userId))) {
            result.put(TrainingDimension.from(row.getDimension()), row.getMastery());
        }
        for (TrainingDimension dimension : TrainingDimension.values()) {
            result.putIfAbsent(dimension, 50);
        }
        return result;
    }

    /** 最近 N 天某维度的掌握度变化量，用于仪表盘 delta */
    public int dimensionDelta(long userId, TrainingDimension dimension, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(Math.max(1, days));
        List<UserKpMasteryLog> logs = logMapper.selectList(new QueryWrapper<UserKpMasteryLog>()
                .eq("user_id", userId)
                .eq("dimension", dimension.name())
                .ge("created_at", from));
        return logs.stream().mapToInt(item -> intValue(item.getDelta())).sum();
    }

    /** 面试报告里的维度分：与已有维度分做一次温和融合，避免覆盖训练结果 */
    public void observeDimension(long userId, TrainingDimension dimension, int score, String source) {
        int value = Math.max(0, Math.min(100, score));
        UserDimensionMastery existing = dimensionMapper.selectOne(new QueryWrapper<UserDimensionMastery>()
                .eq("user_id", userId)
                .eq("dimension", dimension.name())
                .last("LIMIT 1"));
        if (existing == null) {
            UserDimensionMastery row = new UserDimensionMastery();
            row.setId(idGenerator.nextId());
            row.setUserId(userId);
            row.setDimension(dimension.name());
            row.setMastery(value);
            row.setSource(source);
            dimensionMapper.insert(row);
            return;
        }
        int blended = (int) Math.round(intValue(existing.getMastery()) * 0.6 + value * 0.4);
        existing.setMastery(blended);
        existing.setSource(source);
        dimensionMapper.updateById(existing);
    }

    private void recomputeDimension(long userId, String dimension) {
        List<UserKpMastery> rows = masteryMapper.selectList(new QueryWrapper<UserKpMastery>()
                .eq("user_id", userId)
                .eq("dimension", dimension));
        if (rows.isEmpty()) {
            return;
        }
        double weighted = 0;
        double weightSum = 0;
        for (UserKpMastery row : rows) {
            double weight = 1 + Math.log1p(intValue(row.getEvidenceCount()));
            weighted += intValue(row.getMastery()) * weight;
            weightSum += weight;
        }
        int mastery = (int) Math.round(weighted / Math.max(1, weightSum));
        UserDimensionMastery existing = dimensionMapper.selectOne(new QueryWrapper<UserDimensionMastery>()
                .eq("user_id", userId)
                .eq("dimension", dimension)
                .last("LIMIT 1"));
        if (existing == null) {
            UserDimensionMastery row = new UserDimensionMastery();
            row.setId(idGenerator.nextId());
            row.setUserId(userId);
            row.setDimension(dimension);
            row.setMastery(mastery);
            row.setSource("TRAINING");
            dimensionMapper.insert(row);
        } else {
            existing.setMastery(mastery);
            existing.setSource("TRAINING");
            dimensionMapper.updateById(existing);
        }
    }

    private boolean alreadyObserved(long userId, String kpKey, String sourceType,
                                    Long sourceId, Long sourceItemId) {
        QueryWrapper<UserKpMasteryLog> query = new QueryWrapper<UserKpMasteryLog>()
                .eq("user_id", userId)
                .eq("kp_key", kpKey)
                .eq("source_type", sourceType);
        if (sourceId == null) {
            query.isNull("source_id");
        } else {
            query.eq("source_id", sourceId);
        }
        if (sourceItemId == null) {
            query.isNull("source_item_id");
        } else {
            query.eq("source_item_id", sourceItemId);
        }
        return logMapper.selectCount(query) > 0;
    }

    private static boolean isDue(UserKpMastery item, LocalDateTime now) {
        return item.getNextReviewAt() != null && !item.getNextReviewAt().isAfter(now);
    }

    private static long staleDays(UserKpMastery item, LocalDateTime now) {
        LocalDateTime last = item.getLastPracticedAt();
        if (last == null) {
            return STALE_DAYS;
        }
        return Math.max(0, java.time.Duration.between(last, now).toDays());
    }

    private static int reviewDays(int score) {
        if (score <= 2) {
            return 1;
        }
        return score == 3 ? 3 : 7;
    }

    public static String levelOf(int mastery) {
        if (mastery < 40) {
            return "WEAK";
        }
        return mastery >= 75 ? "MASTERED" : "LEARNING";
    }

    /** 一次观测的结果，供训练结果页展示 before/after */
    public record MasteryChange(String kpKey, String kpName, String dimension, int before, int after,
                                int delta, String level, LocalDateTime nextReviewAt,
                                boolean eliminated, boolean changed) {
    }
}
