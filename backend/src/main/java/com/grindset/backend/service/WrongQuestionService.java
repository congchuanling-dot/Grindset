package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.RetryEvaluation;
import com.grindset.backend.dto.RetryResult;
import com.grindset.backend.dto.WrongQuestionDetail;
import com.grindset.backend.dto.WrongQuestionView;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.entity.KnowledgePoint;
import com.grindset.backend.entity.QuestionRetry;
import com.grindset.backend.entity.WrongQuestion;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.mapper.KnowledgePointMapper;
import com.grindset.backend.mapper.QuestionRetryMapper;
import com.grindset.backend.mapper.WrongQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 错题重练闭环:低分题自动收录、错题本查询、单题重练与历史回填。
 *
 * <p>收录和画像回写都是 best-effort 旁路,失败只记日志,不阻塞答题主流程;
 * 重练时模型调用放在事务外,重练记录与状态更新在同一个事务里。</p>
 */
@Service
public class WrongQuestionService {

    private static final Logger log = LoggerFactory.getLogger(WrongQuestionService.class);

    /** 本题得分 <= 2 才收录 */
    private static final int WRONG_SCORE_MAX = 2;
    /** 重练得分 >= 3 标记已掌握 */
    private static final int MASTERED_SCORE_MIN = 3;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_MASTERED = 1;

    private static final int QUESTION_DONE = 3;

    private static final int REVIEW_DAYS_LOW = 1;
    private static final int REVIEW_DAYS_MID = 3;
    private static final int REVIEW_DAYS_HIGH = 7;

    private static final int MAX_ANSWER_LENGTH = 4000;
    private static final int MIN_RETRY_LENGTH = 10;
    private static final int LIST_LIMIT_DEFAULT = 50;
    private static final int LIST_LIMIT_MAX = 200;
    private static final int SNAPSHOT_ANSWER_MAX = 6000;
    private static final int RETRY_HISTORY_LIMIT = 20;
    private static final int PREVIEW_MAX = 120;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionRetryMapper retryMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final KnowledgePointMapper pointMapper;
    private final KnowledgeProfileService knowledgeProfileService;
    private final InterviewLlmService llmService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 单实例内按错题串行化,防止连点重复重练 */
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public WrongQuestionService(WrongQuestionMapper wrongQuestionMapper,
                                QuestionRetryMapper retryMapper,
                                InterviewSessionMapper sessionMapper,
                                InterviewQuestionMapper questionMapper,
                                InterviewMessageMapper messageMapper,
                                KnowledgePointMapper pointMapper,
                                KnowledgeProfileService knowledgeProfileService,
                                InterviewLlmService llmService,
                                SnowflakeIdGenerator idGenerator,
                                TransactionTemplate transactionTemplate) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.retryMapper = retryMapper;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.pointMapper = pointMapper;
        this.knowledgeProfileService = knowledgeProfileService;
        this.llmService = llmService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    // ---------- 收录 ----------

    /** 本题收束后调用;返回错题 ID,未收录返回 null */
    public Long collect(long userId, long sessionId, long questionId) {
        try {
            return transactionTemplate.execute(status -> doCollect(userId, sessionId, questionId));
        } catch (Exception e) {
            log.error("错题收录失败 userId={} sessionId={} questionId={}",
                    userId, sessionId, questionId, e);
            return null;
        }
    }

    private Long doCollect(long userId, long sessionId, long questionId) {
        InterviewQuestion question = questionMapper.selectById(questionId);
        if (!shouldCollect(question)) {
            return null;
        }
        WrongQuestion existing = findByQuestion(userId, questionId);
        if (existing != null) {
            return existing.getId();
        }

        List<InterviewMessage> messages = threadMessages(questionId);
        String questionContent = firstMessage(messages, "QUESTION");
        if (questionContent.isBlank()) {
            return null;
        }

        InterviewSession session = sessionMapper.selectById(sessionId);
        WrongQuestion wrong = new WrongQuestion();
        wrong.setId(idGenerator.nextId());
        wrong.setUserId(userId);
        wrong.setSessionId(sessionId);
        wrong.setQuestionId(questionId);
        wrong.setPosition(session == null ? null : session.getPosition());
        wrong.setRound(session == null ? null : session.getRound());
        wrong.setQuestionContent(questionContent);
        wrong.setQuestionContext(joinMessages(messages, "FOLLOW_UP", "\n"));
        wrong.setOriginalAnswer(truncate(joinMessages(messages, "ANSWER", "\n\n"), SNAPSHOT_ANSWER_MAX));
        wrong.setOriginalScore(question.getScore());
        wrong.setOriginalComment(question.getComment());
        wrong.setOriginalSuggestion(question.getSuggestion());
        wrong.setOriginalFeedback(truncate(joinMessages(messages, "FEEDBACK", "\n"), 1000));
        wrong.setKnowledgePoints(question.getKnowledgePoints());
        wrong.setKpIds(knowledgeProfileService.resolvePointIds(question.getKnowledgePoints()));
        wrong.setStatus(STATUS_PENDING);
        wrong.setRetryCount(0);
        wrong.setNextReviewAt(LocalDateTime.now());

        try {
            wrongQuestionMapper.insert(wrong);
            return wrong.getId();
        } catch (DuplicateKeyException e) {
            WrongQuestion concurrent = findByQuestion(userId, questionId);
            return concurrent == null ? null : concurrent.getId();
        }
    }

    // ---------- 列表与详情 ----------

    /** 错题本列表;summary 全量统计,items 按 status / 知识点 / 会话筛选后返回 */
    public WrongQuestionView list(long userId, String status, Long kpId,
                                  String kpName, Long sessionId, int limit) {
        List<WrongQuestion> all = wrongQuestionMapper.selectList(new QueryWrapper<WrongQuestion>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT 500"));

        LocalDateTime now = LocalDateTime.now();
        int total = all.size();
        int pending = 0;
        int mastered = 0;
        int due = 0;
        for (WrongQuestion wrong : all) {
            int value = intValue(wrong.getStatus());
            if (value == STATUS_MASTERED) {
                mastered++;
            } else {
                pending++;
                if (wrong.getNextReviewAt() == null || !wrong.getNextReviewAt().isAfter(now)) {
                    due++;
                }
            }
        }

        String statusFilter = normalizeStatus(status);
        boolean kpFiltered = kpId != null || (kpName != null && !kpName.isBlank());
        Long resolvedKpId = kpId != null ? kpId : resolveKpId(kpName);

        List<WrongQuestion> matched = all.stream()
                .filter(w -> statusFilter == null
                        || statusFilter.equals(statusLabel(intValue(w.getStatus()))))
                .filter(w -> !kpFiltered
                        || (resolvedKpId != null && containsKp(w.getKpIds(), resolvedKpId)))
                .filter(w -> sessionId == null || sessionId.equals(w.getSessionId()))
                .sorted(wrongComparator())
                .toList();

        int size = limit <= 0 ? LIST_LIMIT_DEFAULT : Math.min(limit, LIST_LIMIT_MAX);
        List<WrongQuestionView.Item> items = matched.stream()
                .limit(size)
                .map(this::toItem)
                .toList();

        return new WrongQuestionView(
                new WrongQuestionView.Summary(total, pending, mastered, due),
                matched.size(), items);
    }

    /** 错题详情;首次重练前隐藏原回答和参考要点 */
    public WrongQuestionDetail detail(long userId, long id) {
        WrongQuestion wrong = requireOwned(userId, id);
        boolean unlocked = intValue(wrong.getRetryCount()) > 0;

        List<WrongQuestionDetail.RetryView> retries = unlocked
                ? retryMapper.selectList(new QueryWrapper<QuestionRetry>()
                        .eq("wrong_id", id)
                        .orderByDesc("created_at")
                        .last("LIMIT " + RETRY_HISTORY_LIMIT))
                .stream().map(this::toRetryView).toList()
                : List.of();

        return new WrongQuestionDetail(
                String.valueOf(wrong.getId()),
                wrong.getQuestionContent(),
                wrong.getQuestionContext(),
                wrong.getKnowledgePoints() == null ? List.of() : wrong.getKnowledgePoints(),
                wrong.getOriginalScore(),
                wrong.getOriginalComment(),
                wrong.getOriginalSuggestion(),
                wrong.getOriginalFeedback(),
                unlocked ? wrong.getOriginalAnswer() : null,
                statusLabel(intValue(wrong.getStatus())),
                intValue(wrong.getRetryCount()),
                wrong.getBestScore(),
                wrong.getLastScore(),
                format(wrong.getNextReviewAt()),
                format(wrong.getCreatedAt()),
                wrong.getSessionId() == null ? null : String.valueOf(wrong.getSessionId()),
                wrong.getPosition(),
                wrong.getRound(),
                retries);
    }

    // ---------- 重练 ----------

    /** 单题重练:模型评分 + 参考要点,写重练记录并回写画像 */
    public RetryResult retry(long userId, long id, String answer) {
        String text = answer == null ? "" : answer.strip();
        if (text.length() < MIN_RETRY_LENGTH) {
            throw new BizException(400, "再想想,至少写 " + MIN_RETRY_LENGTH + " 个字");
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            throw new BizException(400, "回答不能超过 " + MAX_ANSWER_LENGTH + " 字");
        }

        ReentrantLock lock = lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(409, "这道题正在批改,请稍后再试");
        }
        try {
            WrongQuestion wrong = requireOwned(userId, id);

            // 慢调用放在事务外,避免长时间占用数据库连接和行锁
            RetryEvaluation evaluation = llmService.retryEvaluate(wrong, text);

            RetryResult result = transactionTemplate.execute(status -> {
                WrongQuestion locked = wrongQuestionMapper.selectForUpdate(id);
                if (locked == null) {
                    throw new BizException(404, "错题不存在");
                }

                LocalDateTime now = LocalDateTime.now();
                QuestionRetry retry = new QuestionRetry();
                retry.setId(idGenerator.nextId());
                retry.setWrongId(locked.getId());
                retry.setUserId(userId);
                retry.setAnswer(text);
                retry.setScore(evaluation.score());
                retry.setComment(evaluation.comment());
                retry.setSuggestion(evaluation.suggestion());
                retry.setReferenceAnswer(evaluation.referenceAnswer());
                retry.setKnowledgePoints(locked.getKnowledgePoints());
                retry.setCreatedAt(now);
                retryMapper.insert(retry);

                applyRetryStats(locked, evaluation.score(), now);
                wrongQuestionMapper.updateById(locked);

                return new RetryResult(
                        String.valueOf(locked.getId()),
                        evaluation.score(),
                        evaluation.comment(),
                        evaluation.suggestion(),
                        evaluation.referenceAnswer(),
                        statusLabel(intValue(locked.getStatus())),
                        intValue(locked.getRetryCount()),
                        locked.getBestScore(),
                        format(locked.getNextReviewAt()));
            });

            // 画像回写:用快照知识点,失败不影响重练结果,可由重建修复
            try {
                long sourceSessionId = wrong.getSessionId() == null ? 0L : wrong.getSessionId();
                knowledgeProfileService.record(userId, sourceSessionId,
                        wrong.getKnowledgePoints(), evaluation.score());
            } catch (Exception e) {
                log.error("重练画像回写失败 userId={} wrongId={}", userId, id, e);
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    private static void applyRetryStats(WrongQuestion wrong, int score, LocalDateTime now) {
        int oldBest = wrong.getBestScore() == null ? 0 : wrong.getBestScore();
        int nextStatus = statusOf(score);
        wrong.setRetryCount(intValue(wrong.getRetryCount()) + 1);
        wrong.setLastScore(score);
        wrong.setBestScore(Math.max(oldBest, score));
        wrong.setLastRetryAt(now);
        wrong.setStatus(nextStatus);
        wrong.setMasteredAt(nextStatus == STATUS_MASTERED ? now : null);
        wrong.setNextReviewAt(now.plusDays(reviewDays(score)));
    }

    // ---------- 历史回填 ----------

    /** 管理员按用户扫描历史已收束低分题,只补缺,不删除已有错题和重练记录 */
    public RebuildResult rebuild(long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(new QueryWrapper<InterviewSession>()
                .eq("user_id", userId)
                .orderByAsc("created_at"));

        int scanned = 0;
        int created = 0;
        int skipped = 0;
        for (InterviewSession session : sessions) {
            List<InterviewQuestion> questions = questionMapper.selectList(
                    new QueryWrapper<InterviewQuestion>()
                            .eq("session_id", session.getId())
                            .eq("status", QUESTION_DONE)
                            .le("score", WRONG_SCORE_MAX)
                            .orderByAsc("seq"));
            for (InterviewQuestion question : questions) {
                scanned++;
                if (findByQuestion(userId, question.getId()) != null) {
                    skipped++;
                    continue;
                }
                Long id = collect(userId, session.getId(), question.getId());
                if (id != null) {
                    created++;
                } else {
                    skipped++;
                }
            }
        }
        log.info("错题回填完成 userId={} scanned={} created={} skipped={}",
                userId, scanned, created, skipped);
        return new RebuildResult(String.valueOf(userId), scanned, created, skipped);
    }

    // ---------- 规则与工具 ----------

    static boolean shouldCollect(InterviewQuestion question) {
        return question != null
                && intValue(question.getStatus()) == QUESTION_DONE
                && question.getScore() != null
                && question.getScore() <= WRONG_SCORE_MAX;
    }

    static int statusOf(int score) {
        return score >= MASTERED_SCORE_MIN ? STATUS_MASTERED : STATUS_PENDING;
    }

    static int reviewDays(int score) {
        if (score < MASTERED_SCORE_MIN) {
            return REVIEW_DAYS_LOW;
        }
        return score == MASTERED_SCORE_MIN ? REVIEW_DAYS_MID : REVIEW_DAYS_HIGH;
    }

    private WrongQuestion requireOwned(long userId, long id) {
        WrongQuestion wrong = wrongQuestionMapper.selectById(id);
        if (wrong == null || !Objects.equals(wrong.getUserId(), userId)) {
            throw new BizException(404, "错题不存在");
        }
        return wrong;
    }

    private WrongQuestion findByQuestion(long userId, long questionId) {
        return wrongQuestionMapper.selectOne(new QueryWrapper<WrongQuestion>()
                .eq("user_id", userId)
                .eq("question_id", questionId)
                .last("LIMIT 1"));
    }

    private List<InterviewMessage> threadMessages(long questionId) {
        return messageMapper.selectList(new QueryWrapper<InterviewMessage>()
                .eq("question_id", questionId)
                .orderByAsc("seq"));
    }

    private static String firstMessage(List<InterviewMessage> messages, String type) {
        for (InterviewMessage message : messages) {
            if (type.equals(message.getType()) && message.getContent() != null) {
                return message.getContent().strip();
            }
        }
        return "";
    }

    private static String joinMessages(List<InterviewMessage> messages, String type, String delimiter) {
        return messages.stream()
                .filter(message -> type.equals(message.getType()))
                .map(InterviewMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining(delimiter));
    }

    private Long resolveKpId(String kpName) {
        if (kpName == null || kpName.isBlank()) {
            return null;
        }
        String normalized = KnowledgeProfileService.normalize(kpName.strip());
        if (normalized.isEmpty()) {
            return null;
        }
        KnowledgePoint point = pointMapper.selectOne(new QueryWrapper<KnowledgePoint>()
                .eq("normalized_name", normalized)
                .last("LIMIT 1"));
        return point == null ? null : point.getId();
    }

    private static boolean containsKp(List<Long> kpIds, long kpId) {
        return kpIds != null && kpIds.contains(kpId);
    }

    private WrongQuestionView.Item toItem(WrongQuestion wrong) {
        return new WrongQuestionView.Item(
                String.valueOf(wrong.getId()),
                preview(wrong.getQuestionContent()),
                wrong.getKnowledgePoints() == null ? List.of() : wrong.getKnowledgePoints(),
                wrong.getOriginalScore(),
                statusLabel(intValue(wrong.getStatus())),
                intValue(wrong.getRetryCount()),
                wrong.getBestScore(),
                wrong.getLastScore(),
                format(wrong.getLastRetryAt()),
                format(wrong.getNextReviewAt()),
                format(wrong.getCreatedAt()),
                wrong.getSessionId() == null ? null : String.valueOf(wrong.getSessionId()));
    }

    private WrongQuestionDetail.RetryView toRetryView(QuestionRetry retry) {
        return new WrongQuestionDetail.RetryView(
                String.valueOf(retry.getId()),
                retry.getAnswer(),
                intValue(retry.getScore()),
                retry.getComment(),
                retry.getSuggestion(),
                retry.getReferenceAnswer(),
                format(retry.getCreatedAt()));
    }

    private static Comparator<WrongQuestion> wrongComparator() {
        return Comparator.comparingInt((WrongQuestion wrong) -> intValue(wrong.getStatus()))
                .thenComparing(wrong -> wrong.getNextReviewAt() == null
                        ? LocalDateTime.MAX : wrong.getNextReviewAt())
                .thenComparing(wrong -> wrong.getCreatedAt() == null
                        ? LocalDateTime.MIN : wrong.getCreatedAt(), Comparator.reverseOrder());
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replaceAll("\\s+", " ").strip();
        return truncate(text, PREVIEW_MAX);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.strip().toUpperCase(java.util.Locale.ROOT);
        return "PENDING".equals(value) || "MASTERED".equals(value) ? value : null;
    }

    private static String statusLabel(int status) {
        return status == STATUS_MASTERED ? "MASTERED" : "PENDING";
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private ReentrantLock lockOf(long id) {
        return locks.computeIfAbsent(id, key -> new ReentrantLock());
    }

    public record RebuildResult(String userId, int scanned, int created, int skipped) {
    }
}
