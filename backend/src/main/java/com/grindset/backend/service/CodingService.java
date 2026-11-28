package com.grindset.backend.service;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.constant.JudgeLanguage;
import com.grindset.backend.dto.BankEvaluation;
import com.grindset.backend.dto.CodeRunRequest;
import com.grindset.backend.dto.CodeRunResult;
import com.grindset.backend.dto.CodingDetail;
import com.grindset.backend.dto.CodingReviewResult;
import com.grindset.backend.dto.CodingReviewView;
import com.grindset.backend.entity.QuestionBank;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.grindset.backend.common.constant.InputLimits.MAX_CODE_LENGTH;

/**
 * 题库编程题:自由运行 + AI 点评。
 *
 * <p>用户写完整 main,自己构造输入并运行;平台不提供隐藏用例,
 * 提交点评时由大模型检查代码逻辑、边界与潜在错误,并回写做题进度。</p>
 */
@Service
public class CodingService {

    private static final int TIME_LIMIT_DEFAULT = 5000;
    private static final int MEMORY_LIMIT_DEFAULT = 256;

    private final QuestionBankService questionBankService;
    private final JudgeService judgeService;
    private final InterviewLlmService llmService;

    /** 正在运行或点评的用户,避免同一用户并发占用容器 */
    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();

    public CodingService(QuestionBankService questionBankService,
                         JudgeService judgeService,
                         InterviewLlmService llmService) {
        this.questionBankService = questionBankService;
        this.judgeService = judgeService;
        this.llmService = llmService;
    }

    /** 编程题详情:题面、main 骨架与最近一次点评 */
    public CodingDetail detail(long userId, long id) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        return new CodingDetail(
                String.valueOf(question.getId()),
                question.getTitle(),
                question.getContent(),
                question.getType() == null ? null : question.getType().name(),
                question.getDifficulty() == null ? 2 : question.getDifficulty(),
                question.getDomain(),
                question.getKpNames() == null ? List.of() : question.getKpNames(),
                question.getStarterCode() == null ? Map.of() : question.getStarterCode(),
                positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT),
                question.getTags() == null ? List.of() : question.getTags(),
                question.getCompanies() == null ? List.of() : question.getCompanies(),
                question.getLeetcodeNo(),
                question.getLeetcodeSlug(),
                question.getHotScore() == null ? 0 : question.getHotScore(),
                question.getLastAskedAt() == null ? null : question.getLastAskedAt().toString(),
                questionBankService.progressStatus(userId, id));
    }

    /** 运行完整代码,只回传控制台输出,不写点评历史 */
    public CodeRunResult run(long userId, long id, CodeRunRequest request) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        JudgeLanguage language = requireLanguage(request);
        String code = requireCode(request);

        if (!activeUsers.add(userId)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "上一次运行还没结束，请稍候");
        }
        try {
            JudgeService.FreeRunOutcome outcome = execute(question, language, code);
            return new CodeRunResult(outcome.status().name(), outcome.stdout(), outcome.stderr(),
                    outcome.exitCode(), outcome.runtimeMs());
        } finally {
            activeUsers.remove(userId);
        }
    }

    /** 运行代码并让 AI 点评,结果落库并回写做题进度 */
    public CodingReviewResult review(long userId, long id, CodeRunRequest request) {
        QuestionBank question = questionBankService.requireCodingQuestion(id);
        JudgeLanguage language = requireLanguage(request);
        String code = requireCode(request);

        if (!activeUsers.add(userId)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "上一次运行还没结束，请稍候");
        }
        try {
            JudgeService.FreeRunOutcome outcome = execute(question, language, code);
            BankEvaluation evaluation = llmService.bankCodingReview(question, code, language.name(),
                    outcome.status().name(), outcome.stdout(), outcome.stderr(), outcome.exitCode(), userId);
            QuestionBankService.CodingReviewRecord saved = questionBankService.recordCodingReview(
                    userId, id, code, evaluation);
            return new CodingReviewResult(outcome.status().name(), outcome.stdout(), outcome.stderr(),
                    outcome.exitCode(), outcome.runtimeMs(), saved.score(), saved.hitPoints(),
                    saved.missedPoints(), saved.comment(), saved.suggestion(),
                    saved.progressStatus(), saved.attemptCount(), saved.bestScore(), saved.createdAt());
        } finally {
            activeUsers.remove(userId);
        }
    }

    /** 这道题的点评历史,最新在前 */
    public List<CodingReviewView> reviews(long userId, long id) {
        questionBankService.requireCodingQuestion(id);
        return questionBankService.codingReviews(userId, id);
    }

    private JudgeService.FreeRunOutcome execute(QuestionBank question, JudgeLanguage language, String code) {
        return judgeService.runFree(language, code,
                positive(question.getTimeLimitMs(), TIME_LIMIT_DEFAULT),
                positive(question.getMemoryLimitMb(), MEMORY_LIMIT_DEFAULT));
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

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
