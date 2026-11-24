package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.Texts;
import com.grindset.backend.common.constant.JudgeLanguage;
import com.grindset.backend.common.constant.SubmissionStatus;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.grindset.backend.common.constant.InputLimits.MAX_CODE_LENGTH;
import static com.grindset.backend.common.constant.InputLimits.MAX_FEEDBACK_LENGTH;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

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

    /** 支持的编程模式,顺序决定详情接口 codeModes 的顺序 */
    private static final List<String> MODES = List.of("ACM", "LEETCODE");
    private static final String CODE_MODE_ACM = "ACM";
    private static final String CODE_MODE_LEETCODE = "LEETCODE";

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

    /** 编程题详情:两种模式的初始代码与例题,隐藏用例不出库 */
    public CodingDetail detail(long userId, long id) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        List<QuestionTestCase> cases = allCases(id);

        Map<String, List<CodingDetail.SampleCase>> samples = new LinkedHashMap<>();
        List<String> modes = new ArrayList<>();
        for (String mode : MODES) {
            List<CodingDetail.SampleCase> views = cases.stream()
                    .filter(item -> mode.equals(caseMode(item)) && intValue(item.getIsSample()) == 1)
                    .map(item -> new CodingDetail.SampleCase(nullToEmpty(item.getInput()),
                            nullToEmpty(item.getExpectedOutput()), intValue(item.getSort())))
                    .toList();
            samples.put(mode, views);
            boolean hasCases = cases.stream().anyMatch(item -> mode.equals(caseMode(item)));
            if (hasCases) {
                modes.add(mode);
            }
        }

        long submissionCount = submissionMapper.selectCount(new QueryWrapper<QuestionSubmission>()
                .eq("user_id", userId)
                .eq("question_id", id));
        long acceptedCount = submissionMapper.selectCount(new QueryWrapper<QuestionSubmission>()
                .eq("user_id", userId)
                .eq("question_id", id)
                .eq("status", SubmissionStatus.ACCEPTED.name()));

        return new CodingDetail(
                String.valueOf(question.getId()),
                question.getTitle(),
                question.getContent(),
                question.getType() == null ? null : question.getType().name(),
                question.getDifficulty() == null ? 2 : question.getDifficulty(),
                question.getDomain(),
                question.getKpNames() == null ? List.of() : question.getKpNames(),
                modes.isEmpty() ? List.of(CODE_MODE_ACM) : modes,
                question.getStarterCode() == null ? Map.of() : question.getStarterCode(),
                question.getStarterCodeFunction() == null ? Map.of() : question.getStarterCodeFunction(),
                positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT),
                samples,
                questionBankService.progressStatus(userId, id),
                (int) submissionCount,
                acceptedCount > 0,
                question.getFunctionName(),
                question.getParamTypes() == null ? List.of() : question.getParamTypes(),
                question.getReturnType());
    }

    /** 用例所属模式,历史数据没有值时按 ACM 处理 */
    private static String caseMode(QuestionTestCase testCase) {
        return CODE_MODE_LEETCODE.equalsIgnoreCase(testCase.getCaseMode()) ? CODE_MODE_LEETCODE : CODE_MODE_ACM;
    }

    /** 解析请求里的模式:没传就用题目的默认模式,非编程题一律 ACM */
    private static String resolveMode(QuestionBank question, CodeRunRequest request) {
        String raw = request == null ? null : request.codeMode();
        if (raw != null && !raw.isBlank()) {
            String upper = raw.strip().toUpperCase(Locale.ROOT);
            if (MODES.contains(upper)) {
                return upper;
            }
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的编程模式：" + raw);
        }
        return CODE_MODE_LEETCODE.equalsIgnoreCase(question.getCodeMode()) ? CODE_MODE_LEETCODE : CODE_MODE_ACM;
    }

    /** 函数模式下把题目的签名整理成判题器需要的结构 */
    private static JudgeService.FunctionSpec functionSpec(QuestionBank question) {
        List<String> params = question.getParamTypes() == null ? List.of() : question.getParamTypes();
        return new JudgeService.FunctionSpec(question.getFunctionName(), params, question.getReturnType());
    }

    // ---------- 运行样例 ----------

    /** 运行例题,同步返回逐用例结果,不写提交历史 */
    public CodeRunResult run(long userId, long id, CodeRunRequest request) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        String mode = resolveMode(question, request);
        JudgeLanguage language = requireLanguage(request);
        String code = requireCode(request);

        List<QuestionTestCase> samples = sampleCases(id, mode);
        if (samples.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "这道题还没有配置该模式的例题用例");
        }
        if (!activeUsers.add(userId)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "上一次运行还没结束，请稍候");
        }
        try {
            int timeLimit = positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT);
            int memoryLimit = positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT);
            // 函数题走注入驱动的判题,ACM 题直接跑标准输入输出
            JudgeService.JudgeOutcome outcome = CODE_MODE_LEETCODE.equals(mode)
                    ? judgeService.judgeFunction(language, code, functionSpec(question), samples,
                            timeLimit, memoryLimit)
                    : judgeService.judge(language, code, samples, timeLimit, memoryLimit);
            List<CodeRunResult.CaseResult> cases = outcome.cases().stream()
                    .map(item -> new CodeRunResult.CaseResult(item.index(), nullToEmpty(item.input()),
                            nullToEmpty(item.expectedOutput()), nullToEmpty(item.actualOutput()),
                            item.passed(), item.message()))
                    .toList();
            return new CodeRunResult(outcome.status().name(), outcome.passed(), outcome.total(),
                    outcome.runtimeMs(), outcome.errorMessage(), cases);
        } finally {
            activeUsers.remove(userId);
        }
    }

    // ---------- 提交 ----------

    /** 提交代码:落库后异步判题,返回 PENDING 记录供前端轮询 */
    public CodingSubmissionView submit(long userId, long id, CodeRunRequest request) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        String mode = resolveMode(question, request);
        JudgeLanguage language = requireLanguage(request);
        String code = requireCode(request);

        List<QuestionTestCase> cases = allCases(id, mode);
        if (cases.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "这道题还没有配置该模式的测试用例");
        }
        if (!activeUsers.add(userId)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "上一次运行还没结束，请稍候");
        }

        QuestionSubmission submission = new QuestionSubmission();
        submission.setId(idGenerator.nextId());
        submission.setUserId(userId);
        submission.setQuestionId(id);
        submission.setLanguage(language);
        submission.setCode(code);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setPassed(0);
        submission.setTotal(cases.size());
        submission.setCreatedAt(LocalDateTime.now());
        submissionMapper.insert(submission);

        long submissionId = submission.getId();
        executor.submit(() -> judgeAsync(submissionId, userId, question, language, code, cases, mode));
        return toView(submission);
    }

    private void judgeAsync(long submissionId, long userId, QuestionBank question,
                            JudgeLanguage language, String code, List<QuestionTestCase> cases,
                            String mode) {
        try {
            QuestionSubmission running = new QuestionSubmission();
            running.setId(submissionId);
            running.setStatus(SubmissionStatus.RUNNING);
            submissionMapper.updateById(running);

            JudgeService.JudgeOutcome outcome = CODE_MODE_LEETCODE.equals(mode)
                    ? judgeService.judgeFunction(language, code, functionSpec(question), cases,
                            positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                            positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT))
                    : judgeService.judge(language, code, cases,
                            positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                            positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT));

            QuestionSubmission finished = new QuestionSubmission();
            finished.setId(submissionId);
            finished.setStatus(outcome.status());
            finished.setPassed(outcome.passed());
            finished.setTotal(outcome.total());
            finished.setRuntimeMs(outcome.runtimeMs());
            finished.setErrorMessage(Texts.truncate(outcome.errorMessage(), MAX_FEEDBACK_LENGTH));
            finished.setFinishedAt(LocalDateTime.now());
            submissionMapper.updateById(finished);

            boolean accepted = SubmissionStatus.ACCEPTED.equals(outcome.status());
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
            failed.setStatus(SubmissionStatus.SYSTEM_ERROR);
            failed.setErrorMessage(Texts.truncate("判题服务异常：" + e.getMessage(), MAX_FEEDBACK_LENGTH));
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
            throw new BizException(ErrorCode.NOT_FOUND, "提交记录不存在");
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

    private List<QuestionTestCase> sampleCases(long id, String mode) {
        return testCaseMapper.selectList(new QueryWrapper<QuestionTestCase>()
                .eq("question_id", id)
                .eq("case_mode", mode)
                .eq("is_sample", 1)
                .orderByAsc("sort")
                .orderByAsc("id"));
    }

    private List<QuestionTestCase> allCases(long id, String mode) {
        return testCaseMapper.selectList(new QueryWrapper<QuestionTestCase>()
                .eq("question_id", id)
                .eq("case_mode", mode)
                .orderByAsc("is_sample")
                .orderByAsc("sort")
                .orderByAsc("id"));
    }

    /** 两种模式的全部用例,详情页用来按模式分组 */
    private List<QuestionTestCase> allCases(long id) {
        return testCaseMapper.selectList(new QueryWrapper<QuestionTestCase>()
                .eq("question_id", id)
                .orderByAsc("case_mode")
                .orderByAsc("is_sample")
                .orderByAsc("sort")
                .orderByAsc("id"));
    }

    private static JudgeLanguage requireLanguage(CodeRunRequest request) {
        JudgeLanguage language = request == null ? null : JudgeService.parse(request.language());
        if (language == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "暂时只支持 Java 和 Python");
        }
        return language;
    }

    private static String requireCode(CodeRunRequest request) {
        String code = request == null || request.code() == null ? "" : request.code();
        if (code.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先写代码再运行");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "代码不能超过 " + MAX_CODE_LENGTH + " 字符");
        }
        return code;
    }

    private CodingSubmissionView toView(QuestionSubmission submission) {
        return new CodingSubmissionView(
                String.valueOf(submission.getId()),
                String.valueOf(submission.getQuestionId()),
                submission.getLanguage() == null ? null : submission.getLanguage().name(),
                Texts.truncate(submission.getCode(), MAX_CODE_LENGTH),
                submission.getStatus() == null ? null : submission.getStatus().name(),
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

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(STANDARD);
    }
}
