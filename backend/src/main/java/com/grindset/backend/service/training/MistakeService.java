package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.dto.TrainingMistakeView;
import com.grindset.backend.dto.TrainingQuestionSnapshot;
import com.grindset.backend.entity.TrainingMistake;
import com.grindset.backend.mapper.TrainingMistakeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 错题本：答错自动归集，答对高分自动解决，支持按知识点重练。
 */
@Service
public class MistakeService {

    private static final Logger log = LoggerFactory.getLogger(MistakeService.class);

    private final TrainingMistakeMapper mistakeMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public MistakeService(TrainingMistakeMapper mistakeMapper,
                          SnowflakeIdGenerator idGenerator,
                          ObjectMapper objectMapper) {
        this.mistakeMapper = mistakeMapper;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    /** 训练答错：同一道题未解决时更新，不重复堆记录 */
    public TrainingMistake record(long userId, Long planId, Long planItemId,
                                  String kpKey, String kpName, String dimension,
                                  Long questionId, String questionSnapshot,
                                  String answer, int score, List<String> missedPoints,
                                  String comment, String suggestion) {
        if (score > 2) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        TrainingMistake existing = findOpen(userId, kpKey, questionId, planItemId);
        if (existing == null) {
            TrainingMistake row = new TrainingMistake();
            row.setId(idGenerator.nextId());
            row.setUserId(userId);
            row.setPlanId(planId);
            row.setPlanItemId(planItemId);
            row.setKpKey(kpKey);
            row.setKpName(kpName);
            row.setDimension(dimension);
            row.setQuestionId(questionId);
            row.setQuestionSnapshot(questionSnapshot);
            row.setAnswerText(answer);
            row.setScore(score);
            row.setMissedPoints(missedPoints);
            row.setComment(comment);
            row.setSuggestion(suggestion);
            row.setStatus(0);
            row.setReviewCount(0);
            row.setNextReviewAt(now.plusDays(1));
            row.setCreatedAt(now);
            mistakeMapper.insert(row);
            return row;
        }
        existing.setScore(score);
        existing.setAnswerText(answer);
        existing.setMissedPoints(missedPoints);
        existing.setComment(comment);
        existing.setSuggestion(suggestion);
        existing.setReviewCount(intValue(existing.getReviewCount()) + 1);
        existing.setNextReviewAt(now.plusDays(1));
        existing.setUpdatedAt(now);
        mistakeMapper.updateById(existing);
        return existing;
    }

    /** 后续高分作答：解决该知识点的未解决错题 */
    public void resolve(long userId, String kpKey, int score) {
        if (score < 4 || kpKey == null) {
            return;
        }
        TrainingMistake patch = new TrainingMistake();
        patch.setStatus(1);
        patch.setResolvedAt(LocalDateTime.now());
        mistakeMapper.update(patch, new QueryWrapper<TrainingMistake>()
                .eq("user_id", userId)
                .eq("kp_key", kpKey)
                .eq("status", 0));
    }

    public List<TrainingMistakeView> list(long userId, Integer status, int limit) {
        QueryWrapper<TrainingMistake> query = new QueryWrapper<TrainingMistake>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT " + Math.max(1, Math.min(limit, 50)));
        if (status != null) {
            query.eq("status", status);
        }
        List<TrainingMistakeView> result = new ArrayList<>();
        for (TrainingMistake row : mistakeMapper.selectList(query)) {
            result.add(toView(row));
        }
        return result;
    }

    public List<TrainingMistake> open(long userId, int limit) {
        return mistakeMapper.selectList(new QueryWrapper<TrainingMistake>()
                .eq("user_id", userId)
                .eq("status", 0)
                .orderByAsc("next_review_at")
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
    }

    public int openCount(long userId) {
        return Math.toIntExact(mistakeMapper.selectCount(new QueryWrapper<TrainingMistake>()
                .eq("user_id", userId)
                .eq("status", 0)));
    }

    private TrainingMistake findOpen(long userId, String kpKey, Long questionId, Long planItemId) {
        QueryWrapper<TrainingMistake> query = new QueryWrapper<TrainingMistake>()
                .eq("user_id", userId)
                .eq("kp_key", kpKey)
                .eq("status", 0);
        if (questionId != null) {
            query.eq("question_id", questionId);
        } else if (planItemId != null) {
            query.eq("plan_item_id", planItemId);
        }
        return mistakeMapper.selectOne(query.last("LIMIT 1"));
    }

    private TrainingMistakeView toView(TrainingMistake row) {
        TrainingQuestionSnapshot snapshot = parseSnapshot(row.getQuestionSnapshot());
        return new TrainingMistakeView(
                String.valueOf(row.getId()), row.getKpKey(), row.getKpName(),
                row.getDimension(), TrainingDimension.from(row.getDimension()).label(),
                intValue(row.getScore()), intValue(row.getStatus()), intValue(row.getReviewCount()),
                format(row.getNextReviewAt()), format(row.getCreatedAt()),
                snapshot == null ? row.getKpName() : snapshot.title(),
                row.getQuestionId() == null ? null : String.valueOf(row.getQuestionId()),
                snapshot == null ? null : snapshot.content(),
                row.getMissedPoints() == null ? List.of() : row.getMissedPoints(),
                row.getComment(), row.getSuggestion());
    }

    private TrainingQuestionSnapshot parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TrainingQuestionSnapshot.class);
        } catch (JsonProcessingException e) {
            log.warn("错题快照解析失败", e);
            return null;
        }
    }

    private static String format(LocalDateTime time) {
        return time == null ? null
                : com.grindset.backend.common.constant.TimeFormats.STANDARD.format(time);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }
}
