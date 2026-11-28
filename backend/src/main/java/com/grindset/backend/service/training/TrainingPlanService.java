package com.grindset.backend.service.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.TrainingDimension;
import com.grindset.backend.common.TrainingStage;
import com.grindset.backend.common.constant.InputLimits;
import com.grindset.backend.config.TrainingProperties;
import com.grindset.backend.dto.BankEvaluation;
import com.grindset.backend.dto.GeneratedQuestion;
import com.grindset.backend.dto.QuestionAttemptResult;
import com.grindset.backend.dto.SimilarQuestionView;
import com.grindset.backend.dto.TrainingAnswerResult;
import com.grindset.backend.dto.TrainingDashboard;
import com.grindset.backend.dto.TrainingGoal;
import com.grindset.backend.dto.TrainingMistakeView;
import com.grindset.backend.dto.TrainingPlanCreateRequest;
import com.grindset.backend.dto.TrainingPlanDetail;
import com.grindset.backend.dto.TrainingPlanView;
import com.grindset.backend.dto.TrainingQuestionSnapshot;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.entity.TrainingMistake;
import com.grindset.backend.entity.TrainingPlan;
import com.grindset.backend.entity.TrainingPlanItem;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.mapper.QuestionBankMapper;
import com.grindset.backend.mapper.TrainingPlanItemMapper;
import com.grindset.backend.mapper.TrainingPlanMapper;
import com.grindset.backend.service.QuestionBankService;
import com.grindset.backend.service.interview.InterviewContextBuilder;
import com.grindset.backend.service.interview.InterviewSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.grindset.backend.common.constant.InterviewSessionStatus.FINISHED;
import static com.grindset.backend.common.constant.TrainingConstants.ITEM_DONE;
import static com.grindset.backend.common.constant.TrainingConstants.ITEM_TODO;
import static com.grindset.backend.common.constant.TrainingConstants.MIN_ANSWER_LENGTH;
import static com.grindset.backend.common.constant.TrainingConstants.PLAN_COMPLETED;
import static com.grindset.backend.common.constant.TrainingConstants.PLAN_EXPIRED;
import static com.grindset.backend.common.constant.TrainingConstants.PLAN_PENDING;
import static com.grindset.backend.common.constant.TrainingConstants.PLAN_RUNNING;
import static com.grindset.backend.common.constant.TrainingConstants.ROLE_RETEST;

/**
 * 训练会话生命周期：基线/日常/复测/错题重练的生成、作答、结算与仪表盘。
 *
 * <p>选题策略在 {@link AdaptiveTrainingPolicy}，题目来源在 {@link TrainingItemProvider}，
 * 错题在 {@link MistakeService}，仪表盘在 {@link TrainingAnalyticsService}。</p>
 */
@Service
public class TrainingPlanService {

    private static final Logger log = LoggerFactory.getLogger(TrainingPlanService.class);

    private final TrainingPlanMapper planMapper;
    private final TrainingPlanItemMapper itemMapper;
    private final QuestionBankMapper bankMapper;
    private final InterviewSessionService sessionService;
    private final InterviewContextBuilder contextBuilder;
    private final QuestionBankService questionBankService;
    private final MasteryService masteryService;
    private final AdaptiveTrainingPolicy adaptivePolicy;
    private final AdaptiveSessionBuilder sessionBuilder;
    private final TrainingTargetSelector targetSelector;
    private final TrainingViewMapper viewMapper;
    private final TrainingFeedbackService feedbackService;
    private final MistakeService mistakeService;
    private final TrainingAnalyticsService analyticsService;
    private final TrainingLlmService trainingLlmService;
    private final TrainingProperties properties;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public TrainingPlanService(TrainingPlanMapper planMapper,
                               TrainingPlanItemMapper itemMapper,
                               QuestionBankMapper bankMapper,
                               InterviewSessionService sessionService,
                               InterviewContextBuilder contextBuilder,
                               QuestionBankService questionBankService,
                               MasteryService masteryService,
                               AdaptiveTrainingPolicy adaptivePolicy,
                               AdaptiveSessionBuilder sessionBuilder,
                               TrainingTargetSelector targetSelector,
                               TrainingViewMapper viewMapper,
                               TrainingFeedbackService feedbackService,
                               MistakeService mistakeService,
                               TrainingAnalyticsService analyticsService,
                               TrainingLlmService trainingLlmService,
                               TrainingProperties properties,
                               SnowflakeIdGenerator idGenerator,
                               TransactionTemplate transactionTemplate,
                               ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.itemMapper = itemMapper;
        this.bankMapper = bankMapper;
        this.sessionService = sessionService;
        this.contextBuilder = contextBuilder;
        this.questionBankService = questionBankService;
        this.masteryService = masteryService;
        this.adaptivePolicy = adaptivePolicy;
        this.sessionBuilder = sessionBuilder;
        this.targetSelector = targetSelector;
        this.viewMapper = viewMapper;
        this.feedbackService = feedbackService;
        this.mistakeService = mistakeService;
        this.analyticsService = analyticsService;
        this.trainingLlmService = trainingLlmService;
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 生成
    // ------------------------------------------------------------------

    public TrainingPlanDetail create(long userId, TrainingPlanCreateRequest request) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.NOT_FOUND, "弱项训练暂未开放");
        }
        TrainingStage stage = TrainingStage.from(request == null ? null : request.stage());
        expireStale(userId);

        String baseKey = baseSourceKey(stage, userId);
        TrainingPlan existing = findBySourceKey(userId, baseKey);
        boolean reusable = existing != null && intValue(existing.getStatus()) != PLAN_EXPIRED
                && !(stage == TrainingStage.DAILY
                && intValue(existing.getStatus()) == PLAN_COMPLETED);
        if (reusable) {
            return detail(userId, existing.getId());
        }
        String sourceKey = existing == null ? baseKey : baseKey + ":" + System.currentTimeMillis();

        if (stage != TrainingStage.BASELINE && activeCount(userId) >= properties.getMaxActivePlans()) {
            throw new BizException(ErrorCode.CONFLICT, "先完成现有训练，再开新计划");
        }

        SessionContext session = loadSession(userId, request == null ? null : request.sourceId());
        List<TrainingTarget> targets = targetSelector.select(stage, userId, session.id());
        if (targets.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "暂时没有可训练的弱项");
        }
        AdaptiveTrainingPolicy.Blueprint blueprint = adaptivePolicy.plan(stage, targets);
        if (blueprint.slots().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "暂时没有可训练的题目");
        }

        Map<String, Integer> dimensionsBefore = new LinkedHashMap<>();
        masteryService.dimensionMastery(userId)
                .forEach((key, value) -> dimensionsBefore.put(key.name(), value));

        TrainingGenerationContext context = new TrainingGenerationContext(userId, stage,
                stage.name(), session.id(), session.position(), session.company(),
                session.resumeText(), new HashSet<>());
        AdaptiveSessionBuilder.GenerationResult generated = sessionBuilder.build(context, stage, blueprint);

        List<TrainingGoal> goals = targets.stream()
                .map(target -> new TrainingGoal(target.kpKey(), target.kpName(),
                        target.dimension(), target.beforeMastery(), target.severity()))
                .toList();
        String goalJson = toJson(goals);
        String policyJson = policyJson(blueprint, dimensionsBefore);

        final String finalSourceKey = sourceKey;
        long planId;
        try {
            Long created = transactionTemplate.execute(status -> {
                TrainingPlan plan = new TrainingPlan();
                plan.setId(idGenerator.nextId());
                plan.setUserId(userId);
                plan.setSourceType(stage.name());
                plan.setSourceId(session.id() == null ? 0L : session.id());
                plan.setStage(stage.name());
                plan.setSourceKey(finalSourceKey);
                plan.setDimensionFocus(generated.dimensionFocus());
                plan.setTitle(generated.title());
                plan.setGoalJson(goalJson);
                plan.setPolicyJson(policyJson);
                plan.setStatus(PLAN_PENDING);
                plan.setItemCount(generated.items().size());
                plan.setCompletedCount(0);
                plan.setCreatedAt(LocalDateTime.now());
                plan.setExpiresAt(LocalDateTime.now().plusDays(properties.getPlanExpireDays()));
                planMapper.insert(plan);

                int seq = 1;
                for (TrainingItemDraft draft : generated.items()) {
                    TrainingPlanItem item = new TrainingPlanItem();
                    item.setId(idGenerator.nextId());
                    item.setPlanId(plan.getId());
                    item.setUserId(userId);
                    item.setSeq(seq++);
                    item.setItemRole(draft.role());
                    item.setKpKey(draft.kpKey());
                    item.setKpName(draft.kpName());
                    item.setDimension(draft.dimension());
                    item.setItemType(draft.itemType());
                    item.setDifficulty(draft.difficulty());
                    item.setQuestionId(draft.questionId());
                    item.setGeneratedJson(draft.generatedJson());
                    item.setStatus(ITEM_TODO);
                    item.setCreatedAt(LocalDateTime.now());
                    itemMapper.insert(item);
                }
                return plan.getId();
            });
            if (created == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "训练计划创建失败");
            }
            planId = created;
        } catch (DuplicateKeyException e) {
            TrainingPlan concurrent = findBySourceKey(userId, finalSourceKey);
            if (concurrent != null) {
                return detail(userId, concurrent.getId());
            }
            throw e;
        }
        return detail(userId, planId);
    }

    public List<TrainingPlanView> list(long userId, Integer status) {
        expireStale(userId);
        QueryWrapper<TrainingPlan> query = new QueryWrapper<TrainingPlan>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT 50");
        if (status != null) {
            query.eq("status", status);
        }
        return planMapper.selectList(query).stream().map(viewMapper::toPlanView).toList();
    }

    public TrainingPlanDetail detail(long userId, long planId) {
        expireStale(userId);
        return viewMapper.detail(userId, requirePlan(userId, planId));
    }

    // ------------------------------------------------------------------
    // 作答
    // ------------------------------------------------------------------

    public TrainingAnswerResult answer(long userId, long planId, long itemId, String answer) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.NOT_FOUND, "弱项训练暂未开放");
        }
        String text = answer == null ? "" : answer.strip();
        if (text.length() < MIN_ANSWER_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "再想想,至少写 " + MIN_ANSWER_LENGTH + " 个字");
        }
        if (text.length() > InputLimits.MAX_ANSWER_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "回答不能超过 " + InputLimits.MAX_ANSWER_LENGTH + " 字");
        }

        TrainingPlan plan = requirePlan(userId, planId);
        if (plan.getStatus() != null && (plan.getStatus() == PLAN_COMPLETED
                || plan.getStatus() == PLAN_EXPIRED)) {
            throw new BizException(ErrorCode.CONFLICT, "这个训练计划已经结束");
        }
        TrainingPlanItem item = requireItem(userId, planId, itemId);
        if (item.getStatus() != null && item.getStatus() == ITEM_DONE) {
            return viewMapper.alreadyCompleted(userId, plan, item);
        }

        ReentrantLock lock = locks.computeIfAbsent(planId, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "正在批改,请稍后再试");
        }
        try {
            final TrainingPlan currentPlan = requirePlan(userId, planId);
            final TrainingPlanItem currentItem = requireItem(userId, planId, itemId);
            if (currentItem.getStatus() != null && currentItem.getStatus() == ITEM_DONE) {
                return viewMapper.alreadyCompleted(userId, currentPlan, currentItem);
            }

            QuestionBank bank = currentItem.getQuestionId() == null
                    ? null : bankMapper.selectById(currentItem.getQuestionId());
            GeneratedQuestion generated = currentItem.getQuestionId() == null
                    ? viewMapper.parseGenerated(currentItem.getGeneratedJson()) : null;
            if (currentItem.getQuestionId() != null && (bank == null || bank.getStatus() == null
                    || bank.getStatus() != 1)) {
                throw new BizException(ErrorCode.CONFLICT, "题目已下架，请重新生成训练");
            }
            if (currentItem.getQuestionId() == null && generated == null) {
                throw new BizException(ErrorCode.BAD_GATEWAY, "训练题目内容缺失,请重新生成计划");
            }

            int score;
            List<String> hitPoints;
            List<String> missedPoints;
            String comment;
            String suggestion;
            Long attemptId = null;
            if (bank != null) {
                QuestionAttemptResult result = questionBankService.attempt(userId, bank.getId(), text);
                score = result.score();
                hitPoints = result.hitPoints();
                missedPoints = result.missedPoints();
                comment = result.comment();
                suggestion = result.suggestion();
                attemptId = parseNullableId(result.attemptId());
            } else {
                BankEvaluation evaluation = trainingLlmService.evaluate(generated, text, userId, itemId);
                score = evaluation.score();
                hitPoints = evaluation.hitPoints();
                missedPoints = evaluation.missedPoints();
                comment = evaluation.comment();
                suggestion = evaluation.suggestion();
            }

            String questionSnapshot = bank != null
                    ? toJson(new TrainingQuestionSnapshot(bank.getTitle(), bank.getContent(),
                    bank.getKeyPoints() == null ? List.of() : bank.getKeyPoints(),
                    bank.getReferenceAnswer(), bank.getDifficulty()))
                    : currentItem.getGeneratedJson();
            Map<TrainingDimension, Integer> dimensionsBefore = masteryService.dimensionMastery(userId);

            final int finalScore = score;
            final List<String> finalHit = hitPoints;
            final List<String> finalMissed = missedPoints;
            final String finalComment = comment;
            final String finalSuggestion = suggestion;
            final Long finalAttemptId = attemptId;
            final String finalSnapshot = questionSnapshot;

            AnswerTxResult tx = transactionTemplate.execute(status -> {
                LocalDateTime now = LocalDateTime.now();
                int nextDifficulty = adaptivePolicy.nextDifficulty(
                        currentItem.getDifficulty() == null ? 2 : currentItem.getDifficulty(),
                        recentScores(userId, currentItem, finalScore));

                TrainingMistake mistake = finalScore <= 2
                        ? mistakeService.record(userId, planId, itemId, currentItem.getKpKey(),
                        currentItem.getKpName(), currentItem.getDimension(),
                        currentItem.getQuestionId(), finalSnapshot, text, finalScore,
                        finalMissed, finalComment, finalSuggestion)
                        : null;
                mistakeService.resolve(userId, currentItem.getKpKey(), finalScore);

                MasteryService.MasteryChange change = masteryService.observeTraining(
                        userId, planId, itemId, currentItem.getKpKey(), currentItem.getKpName(),
                        currentItem.getDimension(), finalScore, ROLE_RETEST.equals(currentItem.getItemRole()));

                currentItem.setStatus(ITEM_DONE);
                currentItem.setScore(finalScore);
                currentItem.setAttemptId(finalAttemptId);
                currentItem.setMistakeId(mistake == null ? currentItem.getMistakeId() : mistake.getId());
                currentItem.setAnswerText(text);
                currentItem.setHitPoints(finalHit);
                currentItem.setMissedPoints(finalMissed);
                currentItem.setComment(finalComment);
                currentItem.setSuggestion(finalSuggestion);
                currentItem.setFeedbackJson(toJson(Map.of(
                        "score", finalScore,
                        "comment", finalComment == null ? "" : finalComment,
                        "suggestion", finalSuggestion == null ? "" : finalSuggestion)));
                currentItem.setFinishedAt(now);
                itemMapper.updateById(currentItem);

                adjustNextItem(planId, nextDifficulty);

                List<TrainingPlanItem> all = itemsOf(planId);
                int completed = 0;
                int scoreSum = 0;
                for (TrainingPlanItem row : all) {
                    if (row.getStatus() != null && row.getStatus() == ITEM_DONE) {
                        completed++;
                        scoreSum += row.getScore() == null ? 0 : row.getScore();
                    }
                }
                int avgScore = completed == 0 ? 0 : Math.round((float) scoreSum / completed);
                TrainingPlan patch = new TrainingPlan();
                patch.setId(planId);
                patch.setCompletedCount(completed);
                patch.setAvgScore(avgScore);
                patch.setAccuracy(Math.round(avgScore * 20f));
                if (currentPlan.getStatus() == null || currentPlan.getStatus() == PLAN_PENDING) {
                    patch.setStatus(PLAN_RUNNING);
                    patch.setStartedAt(now);
                }
                if (completed >= intValue(currentPlan.getItemCount())) {
                    patch.setStatus(PLAN_COMPLETED);
                    patch.setFinishedAt(now);
                }
                planMapper.updateById(patch);
                return new AnswerTxResult(change, mistake == null ? null : mistake.getId(),
                        nextDifficulty, completed >= intValue(currentPlan.getItemCount()), completed);
            });

            Map<TrainingDimension, Integer> dimensionsAfter = masteryService.dimensionMastery(userId);
            List<SimilarQuestionView> similar = feedbackService.similar(
                    currentItem.getKpKey(), bank == null ? null : bank.getDomain(),
                    currentItem.getQuestionId(), currentItem.getDifficulty(), 2);
            if (!similar.isEmpty()) {
                currentItem.setSimilarJson(toJson(similar));
                try {
                    itemMapper.updateById(currentItem);
                } catch (Exception e) {
                    log.warn("保存同类题失败 planItemId={}", itemId, e);
                }
            }
            analyticsService.snapshot(userId, dimensionsAfter);
            return new TrainingAnswerResult(
                    String.valueOf(itemId), finalScore, finalHit, finalMissed,
                    finalComment, finalSuggestion, false,
                    viewMapper.toDelta(tx.change()),
                    viewMapper.dimensionDelta(currentItem.getDimension(), dimensionsBefore, dimensionsAfter),
                    tx.planCompleted(), tx.completedCount(), intValue(currentPlan.getItemCount()),
                    tx.nextDifficulty(), similar,
                    tx.mistakeId() == null ? null : String.valueOf(tx.mistakeId()),
                    tx.change() != null && tx.change().eliminated());
        } finally {
            lock.unlock();
        }
    }

    public void abandon(long userId, long planId) {
        TrainingPlan plan = requirePlan(userId, planId);
        if (plan.getStatus() != null && plan.getStatus() == PLAN_COMPLETED) {
            throw new BizException(ErrorCode.CONFLICT, "已完成的计划不能放弃");
        }
        TrainingPlan patch = new TrainingPlan();
        patch.setId(planId);
        patch.setStatus(PLAN_EXPIRED);
        patch.setFinishedAt(LocalDateTime.now());
        planMapper.updateById(patch);
    }

    // ------------------------------------------------------------------
    // 仪表盘 / 错题
    // ------------------------------------------------------------------

    public TrainingDashboard dashboard(long userId) {
        return analyticsService.dashboard(userId);
    }

    public List<TrainingMistakeView> mistakes(long userId, Integer status, int limit) {
        return mistakeService.list(userId, status, limit);
    }

    public TrainingPlanDetail retrainMistakes(long userId) {
        return create(userId, new TrainingPlanCreateRequest(TrainingStage.MISTAKE.name(), null));
    }

    // ------------------------------------------------------------------
    // 作答辅助
    // ------------------------------------------------------------------

    private List<Integer> recentScores(long userId, TrainingPlanItem item, int currentScore) {
        List<Integer> scores = new ArrayList<>(masteryService.recentScores(userId, item.getKpKey(), 2));
        if (scores.isEmpty() || scores.get(scores.size() - 1) != currentScore) {
            scores.add(currentScore);
        }
        if (scores.size() > 3) {
            scores = new ArrayList<>(scores.subList(scores.size() - 3, scores.size()));
        }
        return scores;
    }

    /** 下一题难度：更新下一道未完成题的目标难度；题库题尽量换成同难度题 */
    private void adjustNextItem(long planId, int difficulty) {
        TrainingPlanItem next = null;
        for (TrainingPlanItem item : itemsOf(planId)) {
            if (item.getStatus() == null || item.getStatus() == ITEM_TODO) {
                next = item;
                break;
            }
        }
        if (next == null) {
            return;
        }
        next.setDifficulty(difficulty);
        if (next.getQuestionId() != null) {
            QuestionBank replacement = findBankQuestion(next.getKpKey(), difficulty, planId,
                    next.getQuestionId());
            if (replacement != null) {
                next.setQuestionId(replacement.getId());
                next.setDifficulty(replacement.getDifficulty());
            }
        }
        itemMapper.updateById(next);
    }

    private QuestionBank findBankQuestion(String kpKey, int difficulty, long planId, long currentId) {
        List<Long> used = new ArrayList<>();
        for (TrainingPlanItem item : itemsOf(planId)) {
            if (item.getQuestionId() != null && !item.getQuestionId().equals(currentId)) {
                used.add(item.getQuestionId());
            }
        }
        return bankMapper.selectList(new QueryWrapper<QuestionBank>().eq("status", 1)).stream()
                .filter(QuestionKpMatcher::isText)
                .filter(item -> item.getId() != null && !item.getId().equals(currentId)
                        && !used.contains(item.getId()))
                .filter(item -> item.getDifficulty() != null && item.getDifficulty() == difficulty)
                .filter(item -> QuestionKpMatcher.contains(item, kpKey))
                .min(Comparator.comparingLong(QuestionBank::getId))
                .orElse(null);
    }

    private SessionContext loadSession(long userId, String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return new SessionContext(null, "", "", "");
        }
        long sessionId = parseId(sourceId, "面试 ID");
        InterviewSession session = sessionService.requireOwned(userId, sessionId);
        if (session.getStatus() == null || session.getStatus() != FINISHED) {
            throw new BizException(ErrorCode.CONFLICT, "这场面试还没有结束");
        }
        String resumeText;
        try {
            resumeText = contextBuilder.resumeText(userId, session.getResumeId());
        } catch (BizException e) {
            log.warn("训练计划生成时简历不可用 sessionId={} reason={}", sessionId, e.getMessage());
            resumeText = "";
        }
        return new SessionContext(sessionId, session.getPosition(), session.getCompany(), resumeText);
    }

    private static String baseSourceKey(TrainingStage stage, long userId) {
        return switch (stage) {
            case BASELINE -> "BASELINE:" + userId;
            case DAILY -> "DAILY:" + userId + ":" + LocalDate.now();
            case RETEST -> "RETEST:" + userId + ":" + System.currentTimeMillis();
            case MISTAKE -> "MISTAKE:" + userId + ":" + System.currentTimeMillis();
        };
    }

    private TrainingPlan findBySourceKey(long userId, String sourceKey) {
        return planMapper.selectOne(new QueryWrapper<TrainingPlan>()
                .eq("user_id", userId)
                .eq("source_key", sourceKey)
                .last("LIMIT 1"));
    }

    private long activeCount(long userId) {
        return planMapper.selectCount(new QueryWrapper<TrainingPlan>()
                .eq("user_id", userId)
                .in("status", PLAN_PENDING, PLAN_RUNNING));
    }

    private void expireStale(long userId) {
        TrainingPlan patch = new TrainingPlan();
        patch.setStatus(PLAN_EXPIRED);
        planMapper.update(patch, new QueryWrapper<TrainingPlan>()
                .eq("user_id", userId)
                .in("status", PLAN_PENDING, PLAN_RUNNING)
                .lt("expires_at", LocalDateTime.now()));
    }

    private List<TrainingPlanItem> itemsOf(long planId) {
        return itemMapper.selectList(new QueryWrapper<TrainingPlanItem>()
                .eq("plan_id", planId)
                .orderByAsc("seq"));
    }

    private TrainingPlan requirePlan(long userId, long planId) {
        TrainingPlan plan = planMapper.selectById(planId);
        if (plan == null || plan.getUserId() == null || plan.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "训练计划不存在");
        }
        return plan;
    }

    private TrainingPlanItem requireItem(long userId, long planId, long itemId) {
        TrainingPlanItem item = itemMapper.selectById(itemId);
        if (item == null || item.getUserId() == null || item.getUserId() != userId
                || item.getPlanId() == null || item.getPlanId() != planId) {
            throw new BizException(ErrorCode.NOT_FOUND, "训练题目不存在");
        }
        return item;
    }

    private String policyJson(AdaptiveTrainingPolicy.Blueprint blueprint,
                              Map<String, Integer> dimensionsBefore) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("slots", blueprint.slots());
        policy.put("dimensionsBefore", dimensionsBefore);
        return toJson(policy);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "训练计划序列化失败");
        }
    }

    private static long parseId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少" + field);
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + "不合法");
        }
    }

    private static Long parseNullableId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private record SessionContext(Long id, String position, String company, String resumeText) {
    }

    private record AnswerTxResult(MasteryService.MasteryChange change, Long mistakeId,
                                  int nextDifficulty, boolean planCompleted, int completedCount) {
    }
}
