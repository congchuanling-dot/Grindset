package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.KnowledgeProfileView;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.entity.KnowledgePoint;
import com.grindset.backend.entity.UserKnowledgeState;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.mapper.KnowledgePointMapper;
import com.grindset.backend.mapper.UserKnowledgeStateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 掌握度画像:知识点归一、EMA 掌握度、加权候选、画像查询与重建。
 *
 * <p>答题主流程优先:画像更新走独立事务,失败只记日志,不阻塞答题;
 * 历史数据或失败数据通过管理员重建接口修复。</p>
 */
@Service
public class KnowledgeProfileService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeProfileService.class);

    /** 旧掌握度权重,越大越稳定;新观测权重 = 1 - EMA_OLD_WEIGHT */
    private static final double EMA_OLD_WEIGHT = 0.7;
    private static final int CANDIDATE_LIMIT = 5;
    private static final int KNOWN_NAME_LIMIT = 30;
    private static final int WEAK_THRESHOLD = 40;
    private static final int MASTERED_THRESHOLD = 75;
    private static final int STALE_DAYS = 30;
    private static final int QUESTION_DONE = 3;
    private static final int MAX_LIMIT = 200;
    private static final int MIN_POINT_LENGTH = 2;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern PUNCT = Pattern.compile("[\\s\\p{Punct}，。、；：（）【】《》“”‘’·—…]");
    private static final Set<String> LEVELS = Set.of("WEAK", "LEARNING", "MASTERED");

    private final KnowledgePointMapper pointMapper;
    private final UserKnowledgeStateMapper stateMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 重建按用户串行化,避免和实时答题互相覆盖 */
    private final ConcurrentHashMap<Long, ReentrantLock> rebuildLocks = new ConcurrentHashMap<>();

    public KnowledgeProfileService(KnowledgePointMapper pointMapper,
                                   UserKnowledgeStateMapper stateMapper,
                                   InterviewSessionMapper sessionMapper,
                                   InterviewQuestionMapper questionMapper,
                                   SnowflakeIdGenerator idGenerator,
                                   TransactionTemplate transactionTemplate) {
        this.pointMapper = pointMapper;
        this.stateMapper = stateMapper;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    // ---------- 写入 ----------

    /**
     * 答题收束后沉淀。独立事务,best-effort。
     * 同一知识点被多题命中时按调用顺序累加。
     */
    public void record(long userId, long sessionId, List<String> points, Integer score) {
        if (points == null || points.isEmpty() || score == null || score < 1 || score > 5) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> applyAll(userId, sessionId, points, score));
    }

    /** 事务内执行:归一、找/建知识点、行锁、EMA 更新 */
    private void applyAll(long userId, long sessionId, List<String> points, int score) {
        LocalDateTime now = LocalDateTime.now();
        Set<String> seen = new HashSet<>();
        for (String raw : points) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = raw.strip();
            String normalized = normalize(name);
            if (normalized.length() < MIN_POINT_LENGTH || !seen.add(normalized)) {
                continue;
            }
            KnowledgePoint point = findOrCreate(name, normalized);
            UserKnowledgeState state = stateMapper.selectForUpdate(userId, point.getId());
            if (state == null) {
                insertState(userId, sessionId, point.getId(), score, now);
            } else {
                updateState(state, sessionId, score, now);
            }
        }
    }

    private KnowledgePoint findOrCreate(String name, String normalized) {
        KnowledgePoint existing = pointMapper.selectOne(new QueryWrapper<KnowledgePoint>()
                .eq("normalized_name", normalized)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        KnowledgePoint point = new KnowledgePoint();
        point.setId(idGenerator.nextId());
        point.setName(truncate(name, 128));
        point.setNormalizedName(normalized);
        point.setDomain(domainOf(name));
        try {
            pointMapper.insert(point);
            return point;
        } catch (DuplicateKeyException e) {
            // 并发下另一个请求刚插入,重新查一次
            KnowledgePoint concurrent = pointMapper.selectOne(new QueryWrapper<KnowledgePoint>()
                    .eq("normalized_name", normalized)
                    .last("LIMIT 1"));
            if (concurrent == null) {
                throw e;
            }
            return concurrent;
        }
    }

    private void insertState(long userId, long sessionId, long kpId, int score, LocalDateTime now) {
        UserKnowledgeState state = new UserKnowledgeState();
        state.setId(idGenerator.nextId());
        state.setUserId(userId);
        state.setKpId(kpId);
        state.setMastery(observation(score));
        state.setPrevMastery(null);
        state.setPracticeCount(1);
        state.setCorrectCount(score >= 3 ? 1 : 0);
        state.setLastScore(score);
        state.setLastSessionId(sessionId);
        state.setLastPracticedAt(now);
        try {
            stateMapper.insert(state);
        } catch (DuplicateKeyException e) {
            // 极端并发:别人刚插入,转成更新
            UserKnowledgeState concurrent = stateMapper.selectForUpdate(userId, kpId);
            if (concurrent != null) {
                updateState(concurrent, sessionId, score, now);
            }
        }
    }

    private void updateState(UserKnowledgeState state, long sessionId, int score, LocalDateTime now) {
        int old = intValue(state.getMastery());
        int next = (int) Math.round(old * EMA_OLD_WEIGHT + observation(score) * (1 - EMA_OLD_WEIGHT));
        state.setPrevMastery(old);
        state.setMastery(clampMastery(next));
        state.setPracticeCount(intValue(state.getPracticeCount()) + 1);
        state.setCorrectCount(intValue(state.getCorrectCount()) + (score >= 3 ? 1 : 0));
        state.setLastScore(score);
        state.setLastSessionId(sessionId);
        state.setLastPracticedAt(now);
        stateMapper.updateById(state);
    }

    // ---------- 读 ----------

    /** 出题候选:排除本场已考察,按权重随机抽 limit 个 */
    public List<Candidate> weightedCandidates(long userId, List<String> covered, int limit) {
        return weightedCandidates(userId, covered, limit, ThreadLocalRandom.current());
    }

    /** 可注入随机源,便于测试权重与抽样逻辑 */
    List<Candidate> weightedCandidates(long userId, List<String> covered, int limit, java.util.Random random) {
        if (limit <= 0) {
            return List.of();
        }
        List<UserKnowledgeState> states = stateMapper.selectList(new QueryWrapper<UserKnowledgeState>()
                .eq("user_id", userId));
        if (states.isEmpty()) {
            return List.of();
        }

        Map<Long, KnowledgePoint> points = loadPoints(states);
        Set<String> coveredNormalized = covered == null ? Set.of()
                : covered.stream().filter(Objects::nonNull).map(KnowledgeProfileService::normalize)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        List<Weighted> pool = new ArrayList<>();
        for (UserKnowledgeState state : states) {
            KnowledgePoint point = points.get(state.getKpId());
            if (point == null || coveredNormalized.contains(point.getNormalizedName())) {
                continue;
            }
            pool.add(new Weighted(point.getName(), levelLabel(intValue(state.getMastery())),
                    weight(state, now)));
        }
        return sample(pool, Math.min(limit, CANDIDATE_LIMIT), random);
    }

    /** 评分 prompt 复用:取最近练习过的知识点名称,让模型优先复用 */
    public List<String> knownNames(long userId, int limit) {
        int size = Math.max(1, Math.min(limit, KNOWN_NAME_LIMIT));
        List<UserKnowledgeState> states = stateMapper.selectList(new QueryWrapper<UserKnowledgeState>()
                .eq("user_id", userId)
                .orderByDesc("last_practiced_at")
                .last("LIMIT " + size));
        if (states.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgePoint> points = loadPoints(states);
        Set<String> names = new LinkedHashSet<>();
        for (UserKnowledgeState state : states) {
            KnowledgePoint point = points.get(state.getKpId());
            if (point != null) {
                names.add(point.getName());
            }
        }
        return new ArrayList<>(names);
    }

    /** 画像页:summary 全量统计,items 按等级/领域筛选后返回 */
    public KnowledgeProfileView profile(long userId, String level, String domain, int limit) {
        String levelFilter = normalizeFilter(level);
        String domainFilter = domain == null || domain.isBlank() ? null : domain.strip();

        List<UserKnowledgeState> states = stateMapper.selectList(new QueryWrapper<UserKnowledgeState>()
                .eq("user_id", userId));
        Map<Long, KnowledgePoint> points = loadPoints(states);

        int total = 0;
        int weak = 0;
        int learning = 0;
        int mastered = 0;
        int masterySum = 0;
        List<KnowledgeProfileView.StateView> items = new ArrayList<>();

        for (UserKnowledgeState state : states) {
            KnowledgePoint point = points.get(state.getKpId());
            if (point == null) {
                continue;
            }
            int mastery = intValue(state.getMastery());
            String stateLevel = levelOf(mastery);

            total++;
            masterySum += mastery;
            if ("WEAK".equals(stateLevel)) {
                weak++;
            } else if ("LEARNING".equals(stateLevel)) {
                learning++;
            } else {
                mastered++;
            }

            if (levelFilter != null && !levelFilter.equals(stateLevel)) {
                continue;
            }
            if (domainFilter != null && !domainFilter.equals(point.getDomain())) {
                continue;
            }
            items.add(toView(state, point));
        }

        items.sort(Comparator.comparingInt(KnowledgeProfileView.StateView::mastery)
                .thenComparing(view -> view.lastPracticedAt() == null ? "" : view.lastPracticedAt()));

        int size = limit <= 0 ? 100 : Math.min(limit, MAX_LIMIT);
        if (items.size() > size) {
            items = new ArrayList<>(items.subList(0, size));
        }

        int average = total == 0 ? 0 : Math.round((float) masterySum / total);
        return new KnowledgeProfileView(
                new KnowledgeProfileView.Summary(total, weak, learning, mastered, average), items);
    }

    // ---------- 重建 ----------

    /**
     * 按用户从历史已收束题目重放画像。
     * 用于历史数据回填、画像失败修复;会先清空该用户 state。
     */
    public RebuildResult rebuild(long userId) {
        ReentrantLock lock = rebuildLocks.computeIfAbsent(userId, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new BizException(409, "该用户画像正在重建,请稍后再试");
        }
        try {
            List<InterviewSession> sessions = sessionMapper.selectList(new QueryWrapper<InterviewSession>()
                    .eq("user_id", userId)
                    .orderByAsc("created_at"));
            int[] questionCount = {0};

            transactionTemplate.executeWithoutResult(status -> {
                stateMapper.delete(new QueryWrapper<UserKnowledgeState>().eq("user_id", userId));
                for (InterviewSession session : sessions) {
                    List<InterviewQuestion> questions = questionMapper.selectList(
                            new QueryWrapper<InterviewQuestion>()
                                    .eq("session_id", session.getId())
                                    .eq("status", QUESTION_DONE)
                                    .isNotNull("score")
                                    .orderByAsc("seq"));
                    for (InterviewQuestion question : questions) {
                        if (question.getKnowledgePoints() == null || question.getKnowledgePoints().isEmpty()) {
                            continue;
                        }
                        questionCount[0]++;
                        applyAll(userId, session.getId(), question.getKnowledgePoints(),
                                intValue(question.getScore()));
                    }
                }
            });

            long states = stateMapper.selectCount(new QueryWrapper<UserKnowledgeState>()
                    .eq("user_id", userId));
            log.info("画像重建完成 userId={} questions={} states={}", userId, questionCount[0], states);
            return new RebuildResult(String.valueOf(userId), questionCount[0], (int) states);
        } finally {
            lock.unlock();
        }
    }

    // ---------- 算法与工具 ----------

    /** 单次 1~5 分映射为 0~100 观测值 */
    static int observation(int score) {
        return Math.max(0, Math.min(100, (score - 1) * 25));
    }

    /** 归一化:NFKC + 去空白/标点 + 小写;用于合并同一知识点的不同写法 */
    static String normalize(String raw) {
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
        text = PUNCT.matcher(text).replaceAll("");
        return text.length() > 128 ? text.substring(0, 128) : text;
    }

    static String levelOf(int mastery) {
        if (mastery < WEAK_THRESHOLD) {
            return "WEAK";
        }
        return mastery < MASTERED_THRESHOLD ? "LEARNING" : "MASTERED";
    }

    static String levelLabel(int mastery) {
        if (mastery < WEAK_THRESHOLD) {
            return "薄弱";
        }
        return mastery < MASTERED_THRESHOLD ? "待巩固" : "已掌握";
    }

    /** 等级权重 + 错题加成 + 遗忘加成 */
    static double weight(UserKnowledgeState state, LocalDateTime now) {
        int mastery = intValue(state.getMastery());
        double level = mastery < WEAK_THRESHOLD ? 3.0 : mastery < MASTERED_THRESHOLD ? 1.8 : 1.0;
        double wrong = state.getLastScore() != null && state.getLastScore() <= 2 ? 1.5 : 0.0;
        double stale;
        if (state.getLastPracticedAt() == null) {
            stale = 1.0;
        } else {
            long days = Duration.between(state.getLastPracticedAt(), now).toDays();
            stale = Math.min(Math.max(days, 0), STALE_DAYS) / (double) STALE_DAYS;
        }
        return level + wrong + stale;
    }

    private static List<Candidate> sample(List<Weighted> pool, int limit, java.util.Random random) {
        if (pool.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Weighted> copy = new ArrayList<>(pool);
        List<Candidate> result = new ArrayList<>(Math.min(limit, copy.size()));
        while (result.size() < limit && !copy.isEmpty()) {
            double total = 0;
            for (Weighted item : copy) {
                total += item.weight();
            }
            double hit = random.nextDouble() * total;
            double cumulative = 0;
            int index = copy.size() - 1;
            for (int i = 0; i < copy.size(); i++) {
                cumulative += copy.get(i).weight();
                if (hit <= cumulative) {
                    index = i;
                    break;
                }
            }
            Weighted picked = copy.remove(index);
            result.add(new Candidate(picked.name(), picked.level()));
        }
        return result;
    }

    private Map<Long, KnowledgePoint> loadPoints(List<UserKnowledgeState> states) {
        Set<Long> ids = states.stream()
                .map(UserKnowledgeState::getKpId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return pointMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, Function.identity()));
    }

    private KnowledgeProfileView.StateView toView(UserKnowledgeState state, KnowledgePoint point) {
        int mastery = intValue(state.getMastery());
        Integer prev = state.getPrevMastery();
        String trend;
        if (prev == null) {
            trend = "NEW";
        } else if (mastery > prev) {
            trend = "UP";
        } else if (mastery < prev) {
            trend = "DOWN";
        } else {
            trend = "FLAT";
        }
        return new KnowledgeProfileView.StateView(
                String.valueOf(point.getId()), point.getName(), point.getDomain(),
                mastery, prev, levelOf(mastery), trend,
                intValue(state.getPracticeCount()), intValue(state.getCorrectCount()),
                state.getLastScore(),
                state.getLastPracticedAt() == null ? null : state.getLastPracticedAt().format(TIME_FORMAT));
    }

    private static String normalizeFilter(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        String value = level.strip().toUpperCase(Locale.ROOT);
        return LEVELS.contains(value) ? value : null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static int clampMastery(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** 粗粒度领域推断:先匹配具体技术,再匹配泛化方向 */
    static String domainOf(String name) {
        String text = name.toLowerCase(Locale.ROOT);
        if (text.contains("redis")) {
            return "Redis";
        }
        if (containsAny(text, "mysql", "innodb", "索引", "事务隔离", "mvcc", "b+树", "b+ 树")) {
            return "MySQL";
        }
        if (containsAny(text, "jvm", "gc", "垃圾回收", "类加载", "内存模型", "classloader")) {
            return "JVM";
        }
        if (containsAny(text, "tcp", "http", "https", "网络", "握手", "粘包", "dns", "udp")) {
            return "网络";
        }
        if (containsAny(text, "操作系统", "进程", "页表", "虚拟内存", "死锁", "中断", "文件系统")) {
            return "操作系统";
        }
        if (containsAny(text, "算法", "数据结构", "复杂度", "二叉树", "动态规划", "排序", "哈希表")) {
            return "算法";
        }
        if (containsAny(text, "llm", "transformer", "大模型", "rag", "提示词", "embedding", "微调", "agent")) {
            return "AI";
        }
        if (containsAny(text, "项目", "架构", "系统设计", "高并发", "分布式", "消息队列", "kafka", "rocketmq")) {
            return "项目";
        }
        if (containsAny(text, "java", "线程", "并发", "spring", "集合", "hashmap", "synchronized", "aqs")) {
            return "Java";
        }
        return "其他";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record Weighted(String name, String level, double weight) {
    }

    public record Candidate(String name, String level) {
    }

    public record RebuildResult(String userId, int questions, int states) {
    }
}
