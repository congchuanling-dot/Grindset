package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.CodeRunRequest;
import com.grindset.backend.dto.CodeRunResult;
import com.grindset.backend.dto.CodingDetail;
import com.grindset.backend.dto.CodingSubmissionView;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.entity.QuestionSubmission;
import com.grindset.backend.entity.QuestionTestCase;
import com.grindset.backend.mapper.QuestionSubmissionMapper;
import com.grindset.backend.mapper.QuestionTestCaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 编程题:题目视图、运行样例、异步提交与判题历史。
 *
 * <p>提交先落库为 PENDING 再进单线程队列判题,前端轮询;
 * 同一用户同时只允许一个运行/判题任务,防止刷爆判题机。</p>
 */
@Service
public class CodingService {

    private static final Logger log = LoggerFactory.getLogger(CodingService.class);

    private static final int TIME_LIMIT_DEFAULT = 2000;
    private static final int MEMORY_LIMIT_DEFAULT = 256;
    private static final int HISTORY_LIMIT = 20;
    private static final int CODE_MAX_LENGTH = 64 * 1024;
    private static final int STORED_CODE_MAX = 64 * 1024;
    private static final int MESSAGE_MAX_LENGTH = 4000;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final QuestionBankService questionBankService;
    private final QuestionTestCaseMapper testCaseMapper;
    private final QuestionSubmissionMapper submissionMapper;
    private final JudgeService judgeService;
    private final SnowflakeIdGenerator idGenerator;

    /** 判题串行执行,单机够用;后续要扩容再换成队列 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "coding-judge");
        thread.setDaemon(true);
        return thread;
    });

    /** 正在运行样例或判题的用户,避免同一用户并发占用容器 */
    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();

    public CodingService(QuestionBankService questionBankService,
                         QuestionTestCaseMapper testCaseMapper,
                         QuestionSubmissionMapper submissionMapper,
                         JudgeService judgeService,
                         SnowflakeIdGenerator idGenerator) {
        this.questionBankService = questionBankService;
        this.testCaseMapper = testCaseMapper;
        this.submissionMapper = submissionMapper;
        this.judgeService = judgeService;
        this.idGenerator = idGenerator;
    }

    // ---------- 题目 ----------

    /** 编程题详情:只下发例题,隐藏用例不出库 */
    public CodingDetail detail(long userId, long id) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        List<QuestionTestCase> samples = sampleCases(id);
        List<CodingDetail.SampleCase> views = samples.stream()
                .map(item -> new CodingDetail.SampleCase(nullToEmpty(item.getInput()),
                        nullToEmpty(item.getExpectedOutput()), item.getSort() == null ? 0 : item.getSort()))
                .toList();
        long submissionCount = submissionMapper.selectCount(new QueryWrapper<QuestionSubmission>()
                .eq("user_id", userId)
                .eq("question_id", id));
        long acceptedCount = submissionMapper.selectCount(new QueryWrapper<QuestionSubmission>()
                .eq("user_id", userId)
                .eq("question_id", id)
                .eq("status", JudgeService.STATUS_ACCEPTED));

        return new CodingDetail(
                String.valueOf(question.getId()),
                question.getTitle(),
                question.getContent(),
                question.getType(),
                question.getDifficulty() == null ? 2 : question.getDifficulty(),
                question.getDomain(),
                question.getKpNames() == null ? List.of() : question.getKpNames(),
                question.getStarterCode() == null ? java.util.Map.of() : question.getStarterCode(),
                positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT),
                views,
                questionBankService.progressStatus(userId, id),
                (int) submissionCount,
                acceptedCount > 0);
    }

    // ---------- 运行样例 ----------

    /** 运行例题,同步返回逐用例结果,不写提交历史 */
    public CodeRunResult run(long userId, long id, CodeRunRequest request) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        String language = requireLanguage(request);
        String code = requireCode(request);

        List<QuestionTestCase> samples = sampleCases(id);
        if (samples.isEmpty()) {
            throw new BizException(400, "这道题还没有配置例题用例");
        }
        if (!activeUsers.add(userId)) {
            throw new BizException(429, "上一次运行还没结束，请稍候");
        }
        try {
            JudgeService.JudgeOutcome outcome = judgeService.judge(language, code, samples,
                    positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                    positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT));
            List<CodeRunResult.CaseResult> cases = outcome.cases().stream()
                    .map(item -> new CodeRunResult.CaseResult(item.index(), nullToEmpty(item.input()),
                            nullToEmpty(item.expectedOutput()), nullToEmpty(item.actualOutput()),
                            item.passed(), item.message()))
                    .toList();
            return new CodeRunResult(outcome.status(), outcome.passed(), outcome.total(),
                    outcome.runtimeMs(), outcome.errorMessage(), cases);
        } finally {
            activeUsers.remove(userId);
        }
    }

    // ---------- 提交 ----------

    /** 提交代码:落库后异步判题,返回 PENDING 记录供前端轮询 */
    public CodingSubmissionView submit(long userId, long id, CodeRunRequest request) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        String language = requireLanguage(request);
        String code = requireCode(request);

        List<QuestionTestCase> cases = allCases(id);
        if (cases.isEmpty()) {
            throw new BizException(400, "这道题还没有配置测试用例");
        }
        if (!activeUsers.add(userId)) {
            throw new BizException(429, "上一次运行还没结束，请稍候");
        }

        QuestionSubmission submission = new QuestionSubmission();
        submission.setId(idGenerator.nextId());
        submission.setUserId(userId);
        submission.setQuestionId(id);
        submission.setLanguage(language);
        submission.setCode(code);
        submission.setStatus(STATUS_PENDING);
        submission.setPassed(0);
        submission.setTotal(cases.size());
        submission.setCreatedAt(LocalDateTime.now());
        submissionMapper.insert(submission);

        long submissionId = submission.getId();
        executor.submit(() -> judgeAsync(submissionId, userId, question, language, code, cases));
        return toView(submission);
    }

    private void judgeAsync(long submissionId, long userId, QuestionBank question,
                            String language, String code, List<QuestionTestCase> cases) {
        try {
            QuestionSubmission running = new QuestionSubmission();
            running.setId(submissionId);
            running.setStatus(STATUS_RUNNING);
            submissionMapper.updateById(running);

            JudgeService.JudgeOutcome outcome = judgeService.judge(language, code, cases,
                    positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                    positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT));

            QuestionSubmission finished = new QuestionSubmission();
            finished.setId(submissionId);
            finished.setStatus(outcome.status());
            finished.setPassed(outcome.passed());
            finished.setTotal(outcome.total());
            finished.setRuntimeMs(outcome.runtimeMs());
            finished.setErrorMessage(truncate(outcome.errorMessage(), MESSAGE_MAX_LENGTH));
            finished.setFinishedAt(LocalDateTime.now());
            submissionMapper.updateById(finished);

            boolean accepted = JudgeService.STATUS_ACCEPTED.equals(outcome.status());
            try {
                questionBankService.recordCodingOutcome(userId, question.getId(), accepted, code,
                        outcome.errorMessage());
            } catch (Exception e) {
                log.error("编程题结果回写失败 userId={} questionId={}", userId, question.getId(), e);
            }
        } catch (Exception e) {
            log.error("判题异常 submissionId={}", submissionId, e);
            QuestionSubmission failed = new QuestionSubmission();
            failed.setId(submissionId);
            failed.setStatus(JudgeService.STATUS_SYSTEM_ERROR);
            failed.setErrorMessage(truncate("判题服务异常：" + e.getMessage(), MESSAGE_MAX_LENGTH));
            failed.setFinishedAt(LocalDateTime.now());
            submissionMapper.updateById(failed);
        } finally {
            activeUsers.remove(userId);
        }
    }

    // ---------- 历史 ----------

    public CodingSubmissionView submission(long userId, long submissionId) {
        QuestionSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null || submission.getUserId() == null || submission.getUserId() != userId) {
            throw new BizException(404, "提交记录不存在");
        }
        return toView(submission);
    }

    public List<CodingSubmissionView> history(long userId, long id) {
        questionBankService.requireCodingQuestion(id);
        return submissionMapper.selectList(new QueryWrapper<QuestionSubmission>()
                        .eq("user_id", userId)
                        .eq("question_id", id)
                        .orderByDesc("created_at")
                        .last("LIMIT " + HISTORY_LIMIT))
                .stream().map(this::toView).toList();
    }

    // ---------- 工具 ----------

    private List<QuestionTestCase> sampleCases(long id) {
        return testCaseMapper.selectList(new QueryWrapper<QuestionTestCase>()
                .eq("question_id", id)
                .eq("is_sample", 1)
                .orderByAsc("sort")
                .orderByAsc("id"));
    }

    private List<QuestionTestCase> allCases(long id) {
        return testCaseMapper.selectList(new QueryWrapper<QuestionTestCase>()
                .eq("question_id", id)
                .orderByAsc("is_sample")
                .orderByAsc("sort")
                .orderByAsc("id"));
    }

    private static String requireLanguage(CodeRunRequest request) {
        String language = request == null ? null : JudgeService.normalizeLanguage(request.language());
        if (!JudgeService.JAVA.equals(language) && !JudgeService.PYTHON.equals(language)) {
            throw new BizException(400, "暂时只支持 Java 和 Python");
        }
        return language;
    }

    private static String requireCode(CodeRunRequest request) {
        String code = request == null || request.code() == null ? "" : request.code();
        if (code.isBlank()) {
            throw new BizException(400, "请先写代码再运行");
        }
        if (code.length() > CODE_MAX_LENGTH) {
            throw new BizException(400, "代码不能超过 " + CODE_MAX_LENGTH + " 字符");
        }
        return code;
    }

    private CodingSubmissionView toView(QuestionSubmission submission) {
        return new CodingSubmissionView(
                String.valueOf(submission.getId()),
                String.valueOf(submission.getQuestionId()),
                submission.getLanguage(),
                truncate(submission.getCode(), STORED_CODE_MAX),
                submission.getStatus(),
                submission.getPassed() == null ? 0 : submission.getPassed(),
                submission.getTotal() == null ? 0 : submission.getTotal(),
                submission.getRuntimeMs(),
                submission.getErrorMessage(),
                format(submission.getCreatedAt()),
                format(submission.getFinishedAt()));
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
