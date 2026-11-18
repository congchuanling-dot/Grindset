package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.BankEvaluation;
import com.grindset.backend.dto.QuestionAttemptResult;
import com.grindset.backend.dto.QuestionBankDetail;
import com.grindset.backend.dto.QuestionBankImportRequest;
import com.grindset.backend.dto.QuestionBankImportResult;
import com.grindset.backend.dto.QuestionBankView;
import com.grindset.backend.entity.QuestionAttempt;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.entity.UserQuestionProgress;
import com.grindset.backend.mapper.QuestionAttemptMapper;
import com.grindset.backend.mapper.QuestionBankMapper;
import com.grindset.backend.mapper.UserQuestionProgressMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.grindset.backend.common.constant.DisplayLimits.MAX_PREVIEW_CHARS;
import static com.grindset.backend.common.constant.InputLimits.MAX_ANSWER_LENGTH;
import static com.grindset.backend.common.constant.MasteryRules.WRONG_SCORE_MAX;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 题库第一版:列表筛选、题目详情、AI 评分作答、做题进度与错题联动。
 *
 * <p>题目量不大,列表在内存里做筛选和分页;模型调用放在事务外,
 * 错题收录与掌握度回写都是 best-effort 旁路,失败只记日志。</p>
 */
@Service
public class QuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);

    private static final int STATUS_ON = 1;
    private static final int PROGRESS_DONE = 1;
    private static final int PROGRESS_WRONG = 2;

    private static final int PAGE_SIZE_DEFAULT = 20;
    private static final int PAGE_SIZE_MAX = 100;
    private static final int MIN_ANSWER_LENGTH = 10;
    private static final int ATTEMPT_HISTORY_LIMIT = 20;
    private static final int TITLE_MAX = 60;

    private static final Set<String> TYPES = Set.of("BASICS", "SCENARIO", "PROJECT", "CODE");
    private static final Set<String> STATUS_FILTERS = Set.of("TODO", "DONE", "WRONG");

    private final QuestionBankMapper bankMapper;
    private final UserQuestionProgressMapper progressMapper;
    private final QuestionAttemptMapper attemptMapper;
    private final InterviewLlmService llmService;
    private final KnowledgeProfileService knowledgeProfileService;
    private final WrongQuestionService wrongQuestionService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    /** 单实例内按"用户 + 题目"串行化,防止连点重复提交 */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public QuestionBankService(QuestionBankMapper bankMapper,
                               UserQuestionProgressMapper progressMapper,
                               QuestionAttemptMapper attemptMapper,
                               InterviewLlmService llmService,
                               KnowledgeProfileService knowledgeProfileService,
                               WrongQuestionService wrongQuestionService,
                               SnowflakeIdGenerator idGenerator,
                               TransactionTemplate transactionTemplate) {
        this.bankMapper = bankMapper;
        this.progressMapper = progressMapper;
        this.attemptMapper = attemptMapper;
        this.llmService = llmService;
        this.knowledgeProfileService = knowledgeProfileService;
        this.wrongQuestionService = wrongQuestionService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    // ---------- 列表与统计 ----------

    /** 题库列表;summary 全量统计,items 按领域/难度/状态/关键词筛选后分页 */
    public QuestionBankView list(long userId, String domain, Integer difficulty, String status,
                                 String keyword, int page, int size) {
        List<QuestionBank> all = allQuestions();
        Map<Long, UserQuestionProgress> progress = loadProgress(userId);
        QuestionBankView.Summary summary = summarize(all, progress);

        String domainFilter = blankToNull(domain);
        Integer difficultyFilter = difficulty == null ? null : clamp(difficulty, 1, 3);
        String statusFilter = normalizeStatus(status);
        String keywordFilter = blankToNull(keyword);

        List<QuestionBank> matched = all.stream()
                .filter(q -> domainFilter == null || domainFilter.equals(q.getDomain()))
                .filter(q -> difficultyFilter == null || difficultyFilter.equals(q.getDifficulty()))
                .filter(q -> keywordFilter == null || matchesKeyword(q, keywordFilter))
                .filter(q -> statusFilter == null
                        || statusFilter.equals(statusLabel(progress.get(q.getId()))))
                .toList();

        int pageSize = size <= 0 ? PAGE_SIZE_DEFAULT : Math.min(size, PAGE_SIZE_MAX);
        int pageNo = Math.max(page, 1);
        int from = Math.min((pageNo - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());

        List<QuestionBankView.Item> items = matched.subList(from, to).stream()
                .map(q -> toItem(q, progress.get(q.getId())))
                .toList();

        return new QuestionBankView(summary, matched.size(), pageNo, pageSize, items);
    }

    /** 做题统计:只返回 summary,前端刷新进度时用 */
    public QuestionBankView.Summary stats(long userId) {
        return summarize(allQuestions(), loadProgress(userId));
    }

    // ---------- 详情 ----------

    /** 题目详情;首次作答前隐藏参考答案和关键要点 */
    public QuestionBankDetail detail(long userId, long id) {
        QuestionBank question = requireOn(id);
        UserQuestionProgress progress = findProgress(userId, id);
        boolean attempted = progress != null && intValue(progress.getAttemptCount()) > 0;

        List<QuestionBankDetail.AttemptView> attempts = attempted
                ? attemptMapper.selectList(new QueryWrapper<QuestionAttempt>()
                        .eq("user_id", userId)
                        .eq("question_id", id)
                        .orderByDesc("created_at")
                        .last("LIMIT " + ATTEMPT_HISTORY_LIMIT))
                        .stream().map(this::toAttemptView).toList()
                : List.of();

        return new QuestionBankDetail(
                String.valueOf(question.getId()),
                question.getTitle(),
                question.getContent(),
                question.getType(),
                question.getJudgeMode() == null ? "TEXT" : question.getJudgeMode(),
                intValue(question.getDifficulty()),
                question.getDomain(),
                names(question.getKpNames()),
                attempted ? names(question.getKeyPoints()) : List.of(),
                attempted ? question.getReferenceAnswer() : null,
                statusLabel(progress),
                intValue(progress == null ? null : progress.getAttemptCount()),
                progress == null ? null : progress.getBestScore(),
                progress == null ? null : progress.getLastScore(),
                progress == null ? null : format(progress.getLastAttemptAt()),
                attempts);
    }

    /** 某道题的作答历史,最新在前 */
    public List<QuestionBankDetail.AttemptView> attempts(long userId, long id) {
        requireOn(id);
        return attemptMapper.selectList(new QueryWrapper<QuestionAttempt>()
                        .eq("user_id", userId)
                        .eq("question_id", id)
                        .orderByDesc("created_at")
                        .last("LIMIT " + ATTEMPT_HISTORY_LIMIT))
                .stream().map(this::toAttemptView).toList();
    }

    // ---------- 作答 ----------

    /** 提交作答:AI 评分 → 写作答与进度 → 回写掌握度与错题本 */
    public QuestionAttemptResult attempt(long userId, long id, String answer) {
        String text = answer == null ? "" : answer.strip();
        if (text.length() < MIN_ANSWER_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "再想想,至少写 " + MIN_ANSWER_LENGTH + " 个字");
        }
        if (text.length() > MAX_ANSWER_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回答不能超过 " + MAX_ANSWER_LENGTH + " 字");
        }

        QuestionBank question = requireOn(id);
        ReentrantLock lock = lockOf(userId, id);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "这道题正在批改,请稍后再试");
        }
        try {
            // 慢调用放在事务外,避免长时间占用数据库连接
            BankEvaluation evaluation = llmService.bankEvaluate(question, text);
            LocalDateTime now = LocalDateTime.now();

            QuestionAttemptResult result = transactionTemplate.execute(status -> {
                QuestionAttempt attempt = new QuestionAttempt();
                attempt.setId(idGenerator.nextId());
                attempt.setUserId(userId);
                attempt.setQuestionId(id);
                attempt.setAnswer(text);
                attempt.setScore(evaluation.score());
                attempt.setHitPoints(evaluation.hitPoints());
                attempt.setMissedPoints(evaluation.missedPoints());
                attempt.setComment(evaluation.comment());
                attempt.setSuggestion(evaluation.suggestion());
                attempt.setCreatedAt(now);
                attemptMapper.insert(attempt);

                UserQuestionProgress progress = upsertProgress(userId, id, evaluation.score(), now);

                return new QuestionAttemptResult(
                        String.valueOf(attempt.getId()),
                        evaluation.score(),
                        names(evaluation.hitPoints()),
                        names(evaluation.missedPoints()),
                        evaluation.comment(),
                        evaluation.suggestion(),
                        question.getReferenceAnswer(),
                        names(question.getKeyPoints()),
                        capabilityStatus(intValue(progress.getStatus())),
                        intValue(progress.getAttemptCount()),
                        progress.getBestScore(),
                        format(now));
            });

            List<String> points = names(question.getKpNames());
            if (points.isEmpty()) {
                points = names(question.getKeyPoints());
            }
            // 掌握度回写:失败不影响作答结果,可由管理员重建修复
            try {
                knowledgeProfileService.record(userId, 0L, points, evaluation.score());
            } catch (Exception e) {
                log.error("题库掌握度回写失败 userId={} questionId={}", userId, id, e);
            }
            // 低分题进入错题本,复用重练闭环
            if (evaluation.score() <= WRONG_SCORE_MAX) {
                wrongQuestionService.collectFromBank(userId, id, question.getContent(), points,
                        text, evaluation.score(), evaluation.comment(), evaluation.suggestion());
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    // ---------- 编程题联动 ----------

    /** 取编程题;非编程题直接拒绝 */
    public QuestionBank requireCodingQuestion(long id) {
        QuestionBank question = requireOn(id);
        if (!"CODING".equalsIgnoreCase(question.getJudgeMode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "这道题不是编程题");
        }
        return question;
    }

    /** 当前用户在这道题上的状态标签 TODO / DONE / WRONG */
    public String progressStatus(long userId, long id) {
        return statusLabel(findProgress(userId, id));
    }

    /** 判题结束后回写做题进度与掌握度;未通过则收录到错题本 */
    public void recordCodingOutcome(long userId, long id, boolean accepted, String code, String message) {
        QuestionBank question = requireOn(id);
        int score = accepted ? 5 : 1;
        transactionTemplate.executeWithoutResult(status -> upsertProgress(userId, id, score, LocalDateTime.now()));
        try {
            knowledgeProfileService.record(userId, 0L, names(question.getKpNames()), score);
        } catch (Exception e) {
            log.error("编程题掌握度回写失败 userId={} questionId={}", userId, id, e);
        }
        if (!accepted) {
            wrongQuestionService.collectFromBank(userId, id, question.getContent(),
                    names(question.getKpNames()), code, score,
                    "编程题未通过：" + (message == null || message.isBlank() ? "未知原因" : message),
                    "对照例题和边界条件重写代码后重新提交");
        }
    }

    private UserQuestionProgress upsertProgress(long userId, long id, int score, LocalDateTime now) {
        UserQuestionProgress progress = findProgress(userId, id);
        int status = score <= WRONG_SCORE_MAX ? PROGRESS_WRONG : PROGRESS_DONE;
        if (progress == null) {
            progress = new UserQuestionProgress();
            progress.setId(idGenerator.nextId());
            progress.setUserId(userId);
            progress.setQuestionId(id);
            progress.setStatus(status);
            progress.setAttemptCount(1);
            progress.setBestScore(score);
            progress.setLastScore(score);
            progress.setLastAttemptAt(now);
            try {
                progressMapper.insert(progress);
                return progress;
            } catch (DuplicateKeyException e) {
                // 极端并发:别人刚插入,转成更新
                progress = findProgress(userId, id);
                if (progress == null) {
                    throw e;
                }
            }
        }
        progress.setStatus(status);
        progress.setAttemptCount(intValue(progress.getAttemptCount()) + 1);
        progress.setBestScore(Math.max(intValue(progress.getBestScore()), score));
        progress.setLastScore(score);
        progress.setLastAttemptAt(now);
        progressMapper.updateById(progress);
        return progress;
    }

    // ---------- 导入 ----------

    /** 管理端批量导入;按题干 SHA-256 幂等,重复题跳过 */
    public QuestionBankImportResult importQuestions(List<QuestionBankImportRequest.Item> items) {
        if (items == null || items.isEmpty()) {
            return new QuestionBankImportResult(0, 0);
        }
        int imported = 0;
        int skipped = 0;
        for (QuestionBankImportRequest.Item item : items) {
            if (item == null || blankToNull(item.content()) == null) {
                skipped++;
                continue;
            }
            String content = item.content().strip();
            String hash = sha256(content);
            Long exists = bankMapper.selectCount(new QueryWrapper<QuestionBank>()
                    .eq("content_hash", hash));
            if (exists != null && exists > 0) {
                skipped++;
                continue;
            }

            QuestionBank question = new QuestionBank();
            question.setId(idGenerator.nextId());
            question.setTitle(clampTitle(blankToNull(item.title()) == null
                    ? preview(content, TITLE_MAX) : item.title().strip()));
            question.setContent(content);
            question.setType(normalizeType(item.type()));
            question.setDifficulty(item.difficulty() == null ? 2 : clamp(item.difficulty(), 1, 3));
            question.setDomain(blankToNull(item.domain()) == null ? "其他" : item.domain().strip());
            question.setReferenceAnswer(blankToNull(item.referenceAnswer()));
            question.setKeyPoints(limited(item.keyPoints(), 8, 200));
            List<String> kpNames = limited(item.kpNames(), 6, 64);
            question.setKpNames(kpNames.isEmpty() ? question.getKeyPoints() : kpNames);
            question.setKpIds(List.of());
            question.setSource(blankToNull(item.source()) == null ? "MANUAL"
                    : item.source().strip().toUpperCase(Locale.ROOT));
            question.setSourceUrl(blankToNull(item.sourceUrl()));
            question.setContentHash(hash);
            question.setStatus(STATUS_ON);
            try {
                bankMapper.insert(question);
                imported++;
            } catch (DuplicateKeyException e) {
                skipped++;
            }
        }
        return new QuestionBankImportResult(imported, skipped);
    }

    // ---------- 规则与工具 ----------

    private List<QuestionBank> allQuestions() {
        return bankMapper.selectList(new QueryWrapper<QuestionBank>()
                .eq("status", STATUS_ON)
                .orderByAsc("domain")
                .orderByAsc("difficulty")
                .orderByAsc("id"));
    }

    private Map<Long, UserQuestionProgress> loadProgress(long userId) {
        Map<Long, UserQuestionProgress> map = new LinkedHashMap<>();
        for (UserQuestionProgress progress : progressMapper.selectList(
                new QueryWrapper<UserQuestionProgress>().eq("user_id", userId))) {
            map.put(progress.getQuestionId(), progress);
        }
        return map;
    }

    private UserQuestionProgress findProgress(long userId, long id) {
        return progressMapper.selectOne(new QueryWrapper<UserQuestionProgress>()
                .eq("user_id", userId)
                .eq("question_id", id)
                .last("LIMIT 1"));
    }

    private QuestionBankView.Summary summarize(List<QuestionBank> questions,
                                               Map<Long, UserQuestionProgress> progress) {
        int done = 0;
        int wrong = 0;
        int todo = 0;
        int totalAttempts = 0;
        for (QuestionBank question : questions) {
            UserQuestionProgress item = progress.get(question.getId());
            if (item == null || intValue(item.getAttemptCount()) == 0) {
                todo++;
                continue;
            }
            if (intValue(item.getStatus()) == PROGRESS_WRONG) {
                wrong++;
            } else {
                done++;
            }
            totalAttempts += intValue(item.getAttemptCount());
        }
        return new QuestionBankView.Summary(questions.size(), done, wrong, todo, totalAttempts);
    }

    private QuestionBank requireOn(long id) {
        QuestionBank question = bankMapper.selectById(id);
        if (question == null || intValue(question.getStatus()) != STATUS_ON) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在或已下架");
        }
        return question;
    }

    private QuestionBankView.Item toItem(QuestionBank question, UserQuestionProgress progress) {
        return new QuestionBankView.Item(
                String.valueOf(question.getId()),
                question.getTitle(),
                preview(question.getContent(), MAX_PREVIEW_CHARS),
                question.getType(),
                question.getJudgeMode() == null ? "TEXT" : question.getJudgeMode(),
                intValue(question.getDifficulty()),
                question.getDomain(),
                names(question.getKpNames()),
                statusLabel(progress),
                intValue(progress == null ? null : progress.getAttemptCount()),
                progress == null ? null : progress.getBestScore(),
                progress == null ? null : progress.getLastScore(),
                progress == null ? null : format(progress.getLastAttemptAt()));
    }

    private QuestionBankDetail.AttemptView toAttemptView(QuestionAttempt attempt) {
        return new QuestionBankDetail.AttemptView(
                String.valueOf(attempt.getId()),
                attempt.getAnswer(),
                intValue(attempt.getScore()),
                names(attempt.getHitPoints()),
                names(attempt.getMissedPoints()),
                attempt.getComment(),
                attempt.getSuggestion(),
                format(attempt.getCreatedAt()));
    }

    private static boolean matchesKeyword(QuestionBank question, String keyword) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(question.getTitle(), lower)
                || containsIgnoreCase(question.getContent(), lower);
    }

    private static boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private static String statusLabel(UserQuestionProgress progress) {
        if (progress == null || intValue(progress.getAttemptCount()) == 0) {
            return "TODO";
        }
        return intValue(progress.getStatus()) == PROGRESS_WRONG ? "WRONG" : "DONE";
    }

    private static String capabilityStatus(int status) {
        return status == PROGRESS_WRONG ? "WRONG" : "DONE";
    }

    private static String normalizeStatus(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return STATUS_FILTERS.contains(upper) ? upper : null;
    }

    private static String normalizeType(String type) {
        String value = blankToNull(type);
        if (value == null) {
            return "BASICS";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return TYPES.contains(upper) ? upper : "BASICS";
    }

    private static List<String> names(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> limited(List<String> values, int max, int maxLength) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String text = blankToNull(value);
            if (text != null && result.size() < max) {
                result.add(text.length() <= maxLength ? text : text.substring(0, maxLength));
            }
        }
        return result;
    }

    private static String preview(String content, int max) {
        if (content == null) {
            return "";
        }
        String text = content.replaceAll("\\s+", " ").strip();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String clampTitle(String title) {
        return title.length() <= 200 ? title : title.substring(0, 200);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(STANDARD);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private ReentrantLock lockOf(long userId, long questionId) {
        return locks.computeIfAbsent(userId + ":" + questionId, key -> new ReentrantLock());
    }
}
