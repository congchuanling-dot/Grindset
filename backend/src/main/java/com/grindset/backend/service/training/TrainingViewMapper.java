package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.dto.GeneratedQuestion;
import com.grindset.backend.dto.SimilarQuestionView;
import com.grindset.backend.dto.TrainingAnswerResult;
import com.grindset.backend.dto.TrainingEvaluation;
import com.grindset.backend.dto.TrainingGoal;
import com.grindset.backend.dto.TrainingPlanDetail;
import com.grindset.backend.dto.TrainingPlanView;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.entity.TrainingPlan;
import com.grindset.backend.entity.TrainingPlanItem;
import com.grindset.backend.entity.UserKpMastery;
import com.grindset.backend.mapper.QuestionBankMapper;
import com.grindset.backend.mapper.TrainingPlanItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.Numbers.intValue;
import static com.grindset.backend.common.constant.TimeFormats.format;
import static com.grindset.backend.common.constant.TrainingConstants.ITEM_DONE;
import static com.grindset.backend.common.constant.TrainingConstants.PLAN_COMPLETED;
import static com.grindset.backend.common.constant.TrainingConstants.ROLE_RETEST;

/**
 * 训练视图组装：把实体/JSON 转成前端 DTO，避免会话服务同时承担业务与展示。
 */
@Component
public class TrainingViewMapper {

    private static final Logger log = LoggerFactory.getLogger(TrainingViewMapper.class);

    private final TrainingPlanItemMapper itemMapper;
    private final QuestionBankMapper bankMapper;
    private final MasteryService masteryService;
    private final ObjectMapper objectMapper;

    public TrainingViewMapper(TrainingPlanItemMapper itemMapper,
                              QuestionBankMapper bankMapper,
                              MasteryService masteryService,
                              ObjectMapper objectMapper) {
        this.itemMapper = itemMapper;
        this.bankMapper = bankMapper;
        this.masteryService = masteryService;
        this.objectMapper = objectMapper;
    }

    public TrainingPlanDetail detail(long userId, TrainingPlan plan) {
        List<TrainingPlanItem> items = itemsOf(plan.getId());
        List<TrainingGoal> goals = parseGoals(plan.getGoalJson());
        List<TrainingPlanDetail.ItemView> views = new ArrayList<>(items.size());
        for (TrainingPlanItem item : items) {
            views.add(toItemView(item));
        }
        boolean completed = intValue(plan.getStatus()) == PLAN_COMPLETED;
        return new TrainingPlanDetail(String.valueOf(plan.getId()), plan.getTitle(),
                plan.getSourceType(), plan.getStage(), intValue(plan.getStatus()),
                intValue(plan.getItemCount()), intValue(plan.getCompletedCount()),
                intValue(plan.getAccuracy()), plan.getAvgScore(),
                format(plan.getCreatedAt()), format(plan.getExpiresAt()),
                goals.stream().map(goal -> new TrainingPlanDetail.GoalView(
                        goal.kpKey(), goal.kpName(), goal.dimension(),
                        goal.beforeMastery(), goal.severity())).toList(),
                views,
                completed ? resultOf(userId, goals) : List.of(),
                completed ? dimensionResultOf(userId, plan.getPolicyJson()) : List.of());
    }

    public TrainingPlanView toPlanView(TrainingPlan plan) {
        return new TrainingPlanView(String.valueOf(plan.getId()), plan.getTitle(), plan.getSourceType(),
                plan.getStage(), intValue(plan.getStatus()), intValue(plan.getItemCount()),
                intValue(plan.getCompletedCount()), format(plan.getCreatedAt()), format(plan.getExpiresAt()));
    }

    public TrainingPlanDetail.ItemView toItemView(TrainingPlanItem item) {
        QuestionBank bank = item.getQuestionId() == null ? null : bankMapper.selectById(item.getQuestionId());
        GeneratedQuestion generated = item.getQuestionId() == null
                ? parseGenerated(item.getGeneratedJson()) : null;
        boolean bankAvailable = bank != null && (bank.getStatus() == null || bank.getStatus() == 1);
        boolean hideAnswer = ROLE_RETEST.equals(item.getItemRole())
                && (item.getStatus() == null || item.getStatus() != ITEM_DONE);
        String title = bankAvailable && bank != null ? bank.getTitle()
                : generated != null ? generated.title() : "题目已下架";
        String content = bankAvailable && bank != null ? bank.getContent()
                : generated != null ? generated.content() : "该题目已下架，可跳过";
        List<String> keyPoints = bankAvailable && bank != null ? safe(bank.getKeyPoints())
                : generated != null ? safe(generated.keyPoints()) : List.of();
        Integer difficulty = bankAvailable && bank != null ? bank.getDifficulty()
                : generated != null ? generated.difficulty() : item.getDifficulty();
        String reference = bankAvailable && bank != null ? bank.getReferenceAnswer()
                : generated != null ? generated.referenceAnswer() : null;
        TrainingDimension dimension = TrainingDimension.from(item.getDimension());
        TrainingEvaluation evaluation = parseEvaluation(item.getFeedbackJson());
        return new TrainingPlanDetail.ItemView(
                String.valueOf(item.getId()), intValue(item.getSeq()), item.getItemRole(),
                item.getKpKey(), item.getKpName(), dimension.name(), dimension.label(),
                item.getItemType(), item.getQuestionId() == null ? "AI" : "BANK",
                item.getQuestionId() == null ? null : String.valueOf(item.getQuestionId()),
                title, content, hideAnswer ? List.of() : keyPoints, difficulty,
                intValue(item.getStatus()), item.getScore(),
                safe(item.getHitPoints()), safe(item.getMissedPoints()),
                item.getComment(), item.getSuggestion(),
                evaluation == null ? null : evaluation.summary(),
                hideAnswer || evaluation == null ? null : evaluation.detailedFeedback(),
                hideAnswer || evaluation == null ? List.of() : safe(evaluation.improvements()),
                hideAnswer ? null : standardReference(evaluation, reference),
                evaluation == null ? null : evaluation.questionType(),
                hideAnswer || evaluation == null ? List.of() : safe(evaluation.scoringPoints()),
                format(item.getFinishedAt()),
                hideAnswer ? List.of() : parseSimilar(item.getSimilarJson()),
                item.getMistakeId() == null ? null : String.valueOf(item.getMistakeId()));
    }

    public TrainingAnswerResult alreadyCompleted(long userId, TrainingPlan plan, TrainingPlanItem item) {
        UserKpMastery mastery = masteryService.find(userId, item.getKpKey());
        int current = mastery == null ? 0 : intValue(mastery.getMastery());
        TrainingDimension dimension = TrainingDimension.from(item.getDimension());
        TrainingPlanDetail.MasteryDelta delta = mastery == null ? null
                : new TrainingPlanDetail.MasteryDelta(item.getKpKey(), item.getKpName(),
                dimension.name(), current, current, 0, mastery.getLevel(),
                format(mastery.getNextReviewAt()), mastery.getEliminatedAt() != null);
        TrainingPlanDetail.DimensionDelta dimDelta = new TrainingPlanDetail.DimensionDelta(
                dimension.name(), dimension.label(), current, current, 0);
        TrainingEvaluation evaluation = parseEvaluation(item.getFeedbackJson());
        return new TrainingAnswerResult(String.valueOf(item.getId()), intValue(item.getScore()),
                safe(item.getHitPoints()), safe(item.getMissedPoints()), item.getComment(),
                item.getSuggestion(), true, delta, dimDelta,
                intValue(plan.getStatus()) == PLAN_COMPLETED,
                intValue(plan.getCompletedCount()), intValue(plan.getItemCount()),
                item.getDifficulty() == null ? 2 : item.getDifficulty(),
                parseSimilar(item.getSimilarJson()),
                item.getMistakeId() == null ? null : String.valueOf(item.getMistakeId()),
                mastery != null && mastery.getEliminatedAt() != null,
                evaluation == null ? item.getComment() : evaluation.summary(),
                evaluation == null ? null : evaluation.detailedFeedback(),
                evaluation == null ? List.of() : safe(evaluation.improvements()),
                evaluation == null ? null : evaluation.referenceAnswer(),
                evaluation == null ? null : evaluation.questionType(),
                evaluation == null ? List.of() : safe(evaluation.scoringPoints()));
    }

    public TrainingPlanDetail.MasteryDelta toDelta(MasteryService.MasteryChange change) {
        if (change == null) {
            return null;
        }
        TrainingDimension dimension = TrainingDimension.from(change.dimension());
        return new TrainingPlanDetail.MasteryDelta(change.kpKey(), change.kpName(),
                dimension.name(), change.before(), change.after(), change.delta(),
                change.level(), format(change.nextReviewAt()), change.eliminated());
    }

    public TrainingPlanDetail.DimensionDelta dimensionDelta(String dimensionCode,
                                                            Map<TrainingDimension, Integer> before,
                                                            Map<TrainingDimension, Integer> after) {
        TrainingDimension dimension = TrainingDimension.from(dimensionCode);
        int beforeValue = before.getOrDefault(dimension, 50);
        int afterValue = after.getOrDefault(dimension, beforeValue);
        return new TrainingPlanDetail.DimensionDelta(dimension.name(), dimension.label(),
                beforeValue, afterValue, afterValue - beforeValue);
    }

    public GeneratedQuestion parseGenerated(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, GeneratedQuestion.class);
        } catch (JsonProcessingException e) {
            log.warn("训练题 JSON 解析失败", e);
            return null;
        }
    }

    private TrainingEvaluation parseEvaluation(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TrainingEvaluation.class);
        } catch (JsonProcessingException e) {
            log.warn("训练批改 JSON 解析失败", e);
            return null;
        }
    }

    private List<TrainingPlanItem> itemsOf(long planId) {
        return itemMapper.selectList(new QueryWrapper<TrainingPlanItem>()
                .eq("plan_id", planId)
                .orderByAsc("seq"));
    }

    private List<TrainingPlanDetail.MasteryDelta> resultOf(long userId, List<TrainingGoal> goals) {
        List<TrainingPlanDetail.MasteryDelta> result = new ArrayList<>(goals.size());
        for (TrainingGoal goal : goals) {
            UserKpMastery mastery = masteryService.find(userId, goal.kpKey());
            int after = mastery == null ? goal.beforeMastery() : intValue(mastery.getMastery());
            TrainingDimension dimension = TrainingDimension.from(goal.dimension());
            result.add(new TrainingPlanDetail.MasteryDelta(goal.kpKey(), goal.kpName(),
                    dimension.name(), goal.beforeMastery(), after, after - goal.beforeMastery(),
                    mastery == null ? MasteryService.levelOf(after) : mastery.getLevel(),
                    mastery == null ? null : format(mastery.getNextReviewAt()),
                    mastery != null && mastery.getEliminatedAt() != null));
        }
        return result;
    }

    private List<TrainingPlanDetail.DimensionDelta> dimensionResultOf(long userId, String policyJson) {
        Map<String, Integer> before = parseDimensionsBefore(policyJson);
        if (before.isEmpty()) {
            return List.of();
        }
        Map<TrainingDimension, Integer> after = masteryService.dimensionMastery(userId);
        List<TrainingPlanDetail.DimensionDelta> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            TrainingDimension dimension = TrainingDimension.from(entry.getKey());
            int afterValue = after.getOrDefault(dimension, entry.getValue());
            result.add(new TrainingPlanDetail.DimensionDelta(dimension.name(), dimension.label(),
                    entry.getValue(), afterValue, afterValue - entry.getValue()));
        }
        return result;
    }

    private List<TrainingGoal> parseGoals(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TrainingGoal>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("训练目标 JSON 解析失败", e);
            return List.of();
        }
    }

    private Map<String, Integer> parseDimensionsBefore(String policyJson) {
        if (policyJson == null || policyJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(policyJson);
            JsonNode before = root.get("dimensionsBefore");
            if (before == null || !before.isObject()) {
                return Map.of();
            }
            Map<String, Integer> result = new LinkedHashMap<>();
            before.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asInt()));
            return result;
        } catch (JsonProcessingException e) {
            log.warn("训练策略 JSON 解析失败", e);
            return Map.of();
        }
    }

    private List<SimilarQuestionView> parseSimilar(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SimilarQuestionView>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("同类题 JSON 解析失败", e);
            return List.of();
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** 已批改的题优先展示阅卷生成的标准答案，未批改时回落题库/生成时的参考答案。 */
    private static String standardReference(TrainingEvaluation evaluation, String fallback) {
        String reference = evaluation == null ? null : evaluation.referenceAnswer();
        return reference == null || reference.isBlank() ? fallback : reference;
    }


}
