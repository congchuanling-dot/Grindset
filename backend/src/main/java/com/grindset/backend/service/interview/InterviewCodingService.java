package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.FreeRunStatus;
import com.grindset.backend.common.InterviewQuestionType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.Texts;
import com.grindset.backend.common.constant.InputLimits;
import com.grindset.backend.common.constant.QuestionStatus;
import com.grindset.backend.dto.CodeRunRequest;
import com.grindset.backend.dto.CodingChatRequest;
import com.grindset.backend.dto.InterviewCodingResultView;
import com.grindset.backend.dto.InterviewCodingView;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.entity.QuestionBank;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.service.InterviewLlmService;
import com.grindset.backend.service.JudgeService;
import com.grindset.backend.service.QuestionBankService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.grindset.backend.common.constant.InterviewSessionStatus.ONGOING;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;
import static com.grindset.backend.service.interview.InterviewConstants.DEFAULT_CODING_TIME_LIMIT_SEC;
import static com.grindset.backend.service.interview.InterviewConstants.ROLE_INTERVIEWER;
import static com.grindset.backend.service.interview.InterviewConstants.ROLE_USER;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_CODING_CHAT;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_QUESTION;
import static com.grindset.backend.service.interview.InterviewViews.intValue;

/**
 * 面试最后一题:FREE_RUN 开放式手撕代码。
 *
 * <p>平台只给 main 骨架,不提供测试用例;候选人写完整程序后点击运行,
 * 面试官看控制台输出和代码过程,而不是看力扣式 AC/WA。</p>
 */
@Service
public class InterviewCodingService {

    private static final Logger log = LoggerFactory.getLogger(InterviewCodingService.class);

    private static final String CODE_MODE_FREE = "FREE";
    private static final String DEFAULT_LANGUAGE = "JAVA";
    private static final int FREE_RUN_TIME_LIMIT_MS = 5000;
    private static final int FREE_RUN_MEMORY_MB = 256;
    private static final int MAX_CHAT_MESSAGE = 2000;
    private static final int HISTORY_LIMIT = 20;
    private static final int DEADLINE_GRACE_SECONDS = 30;

    private static final String INTRO = "最后一道题是开放式手撕代码。平台只提供 main 骨架，没有测试用例模板。"
            + "你可以随时用语音问我思路、边界或复杂度；写完后点运行，通过控制台输出调试。";

    private static final Map<String, String> STARTER_CODE = starterCode();

    private final InterviewSessionService sessionService;
    private final InterviewSessionLocks sessionLocks;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final QuestionBankService questionBankService;
    private final JudgeService judgeService;
    private final InterviewLlmService llmService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** 同一道编程题只允许一个面试官回复在生成,避免消息 seq 冲突 */
    private final ConcurrentHashMap<Long, ReentrantLock> chatLocks = new ConcurrentHashMap<>();

    public InterviewCodingService(InterviewSessionService sessionService,
                                  InterviewSessionLocks sessionLocks,
                                  InterviewSessionMapper sessionMapper,
                                  InterviewQuestionMapper questionMapper,
                                  InterviewMessageMapper messageMapper,
                                  QuestionBankService questionBankService,
                                  JudgeService judgeService,
                                  InterviewLlmService llmService,
                                  SnowflakeIdGenerator idGenerator,
                                  TransactionTemplate transactionTemplate,
                                  ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.sessionLocks = sessionLocks;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.questionBankService = questionBankService;
        this.judgeService = judgeService;
        this.llmService = llmService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** 开始编程题;刷新后重复调用直接返回已有题目 */
    public InterviewCodingView start(long userId, long sessionId) {
        ReentrantLock lock = sessionLocks.lockOf(sessionId);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, sessionId);
            ensureOngoing(session);
            if (intValue(session.getCodingEnabled()) != 1) {
                throw new BizException(ErrorCode.BAD_REQUEST, "本场面试没有开启手撕代码");
            }

            InterviewQuestion existing = codingQuestion(sessionId);
            if (existing != null) {
                return view(existing);
            }

            int completed = intValue(session.getCompletedQuestions());
            if (completed != intValue(session.getQuestionCount()) - 1) {
                throw new BizException(ErrorCode.CONFLICT, "还没到编程题环节");
            }

            QuestionBank question = questionBankService.pickFreeCodingQuestion(
                    pickDifficulty(session), recentCodingQuestionIds(userId));
            if (question == null) {
                // 没有可用编程题就自动降级,让最后一道语音题顶上
                session.setCodingEnabled(0);
                sessionMapper.updateById(session);
                return new InterviewCodingView(null, completed + 1, true, null, null, null,
                        0, null, List.of(), "暂时没有可用的编程题，已跳过手撕代码环节。",
                        null, null, Map.of(), null, null, null);
            }

            int limit = positive(session.getCodingTimeLimitSec(), DEFAULT_CODING_TIME_LIMIT_SEC);
            LocalDateTime now = LocalDateTime.now();
            InterviewQuestion coding = new InterviewQuestion();
            coding.setId(idGenerator.nextId());
            coding.setSessionId(sessionId);
            coding.setSeq(completed + 1);
            coding.setQuestionType(InterviewQuestionType.CODING);
            coding.setCodingQuestionId(question.getId());
            coding.setCodingTitle(question.getTitle());
            coding.setCodingMode(CODE_MODE_FREE);
            coding.setStatus(QuestionStatus.ASKING);
            coding.setFollowUpCount(0);
            coding.setStartedAt(now);
            coding.setDeadlineAt(now.plusSeconds(limit));
            coding.setCreatedAt(now);
            coding.setRunCount(0);
            questionMapper.insert(coding);

            messageMapper.insert(newMessage(coding.getId(), 1, ROLE_INTERVIEWER, TYPE_QUESTION, INTRO));

            // 把剩余时间回写到会话配置里,刷新后仍能显示同一个时限
            session.setCodingTimeLimitSec(limit);
            sessionMapper.updateById(session);
            return view(coding);
        } finally {
            lock.unlock();
        }
    }

    /** 运行当前完整代码,不比对测试用例,只返回控制台输出 */
    public InterviewCodingView.RunView run(long userId, long sessionId, CodeRunRequest request) {
        InterviewSession session = sessionService.requireOwned(userId, sessionId);
        ensureOngoing(session);
        InterviewQuestion coding = requireCodingQuestion(sessionId);
        ensureNotExpired(coding);

        String code = request == null ? null : request.code();
        String language = request == null ? null : request.language();
        updateDraft(coding, code, language, CODE_MODE_FREE);
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先写代码再运行");
        }
        if (code.length() > InputLimits.MAX_CODE_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "代码不能超过 " + InputLimits.MAX_CODE_LENGTH + " 字符");
        }
        var parsedLanguage = JudgeService.parse(language);
        if (parsedLanguage == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "暂时只支持 Java 和 Python");
        }

        JudgeService.FreeRunOutcome outcome = judgeService.runFree(
                parsedLanguage, code, FREE_RUN_TIME_LIMIT_MS, FREE_RUN_MEMORY_MB);

        String stdout = Texts.truncate(outcome.stdout(), 20000);
        String stderr = Texts.truncate(outcome.stderr(), 20000);
        LocalDateTime now = LocalDateTime.now();

        coding.setJudgeStatus(outcome.status().name());
        coding.setRunStdout(stdout);
        coding.setRunStderr(stderr);
        coding.setRunExitCode(outcome.exitCode());
        coding.setRunRuntimeMs(outcome.runtimeMs());
        coding.setRunAt(now);
        coding.setRunCount(intValue(coding.getRunCount()) + 1);
        coding.setCodeDraft(code);
        coding.setCodeLanguage(parsedLanguage.name());
        questionMapper.updateById(coding);

        return new InterviewCodingView.RunView(outcome.status().name(), stdout, stderr,
                outcome.exitCode(), outcome.runtimeMs(), format(now));
    }

    /** 保存代码草稿,刷新页面和面试官交流都靠它 */
    public void saveDraft(long userId, long sessionId, CodeRunRequest request) {
        InterviewSession session = sessionService.requireOwned(userId, sessionId);
        ensureOngoing(session);
        InterviewQuestion coding = requireCodingQuestion(sessionId);
        updateDraft(coding, request == null ? null : request.code(),
                request == null ? null : request.language(), CODE_MODE_FREE);
    }

    /** 完成本题:根据最近一次运行结果和代码给分,不依赖隐藏用例 */
    public InterviewCodingResultView complete(long userId, long sessionId) {
        return complete(userId, sessionId, true);
    }

    /**
     * 复盘前兜底:如果已经运行过但用户没点“完成本题”,自动结算,避免丢结果。
     */
    public InterviewCodingResultView completeLatest(long userId, long sessionId) {
        InterviewQuestion coding = codingQuestion(sessionId);
        if (coding == null || intValue(coding.getStatus()) == QuestionStatus.DONE
                || coding.getRunAt() == null) {
            return null;
        }
        return complete(userId, sessionId, false);
    }

    private InterviewCodingResultView complete(long userId, long sessionId, boolean withFeedback) {
        ReentrantLock lock = sessionLocks.lockOf(sessionId);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, sessionId);
            ensureOngoing(session);
            InterviewQuestion coding = requireCodingQuestion(sessionId);
            if (intValue(coding.getStatus()) == QuestionStatus.DONE) {
                return resultView(session, coding);
            }

            QuestionBank question = questionBankService.requireCodingQuestion(coding.getCodingQuestionId());
            String code = coding.getCodeDraft();
            int score = fallbackScore(coding);
            String comment = fallbackComment(coding);
            String suggestion = fallbackSuggestion(coding);

            if (withFeedback) {
                try {
                    InterviewLlmService.CodingFeedback feedback = llmService.codingFeedback(
                            session, question, code, coding.getJudgeStatus(),
                            coding.getRunStdout(), coding.getRunStderr(), coding.getRunExitCode());
                    if (feedback != null) {
                        if (feedback.score() != null && feedback.score() >= 1 && feedback.score() <= 5) {
                            score = feedback.score();
                        }
                        if (feedback.comment() != null && !feedback.comment().isBlank()) {
                            comment = feedback.comment();
                        }
                        if (feedback.suggestion() != null && !feedback.suggestion().isBlank()) {
                            suggestion = feedback.suggestion();
                        }
                    }
                } catch (Exception e) {
                    log.warn("面试编程点评生成失败,使用兜底文案 sessionId={}", sessionId, e);
                }
            }

            int finalScore = score;
            String finalComment = comment;
            String finalSuggestion = suggestion;
            LocalDateTime now = LocalDateTime.now();

            transactionTemplate.executeWithoutResult(status -> {
                coding.setScore(finalScore);
                coding.setComment(finalComment);
                coding.setSuggestion(finalSuggestion);
                coding.setKnowledgePoints(question.getKpNames() == null ? List.of() : question.getKpNames());
                coding.setEvaluationJson(toJson(Map.of(
                        "status", coding.getJudgeStatus() == null ? "NOT_RUN" : coding.getJudgeStatus(),
                        "exitCode", coding.getRunExitCode() == null ? -1 : coding.getRunExitCode(),
                        "runtimeMs", coding.getRunRuntimeMs() == null ? 0 : coding.getRunRuntimeMs())));
                coding.setStatus(QuestionStatus.DONE);
                coding.setFinishedAt(now);
                questionMapper.updateById(coding);

                int completed = Math.min(intValue(session.getQuestionCount()),
                        intValue(session.getCompletedQuestions()) + 1);
                session.setCompletedQuestions(completed);
                sessionMapper.updateById(session);
            });

            return resultView(session, coding);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 编程阶段的面试官交流:持久化用户消息,流式生成回复,结束后落库。
     * 当前代码由前端随请求带上,最近运行结果从数据库读取。
     */
    public void streamChat(long userId, long sessionId, CodingChatRequest request,
                           Consumer<String> onDelta) {
        InterviewSession session = sessionService.requireOwned(userId, sessionId);
        ensureOngoing(session);
        InterviewQuestion coding = requireCodingQuestion(sessionId);

        String message = request == null || request.message() == null ? "" : request.message().strip();
        if (message.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "想问什么?说一句再发");
        }
        if (message.length() > MAX_CHAT_MESSAGE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "一次最多问 " + MAX_CHAT_MESSAGE + " 字");
        }

        String code = request.code() == null ? coding.getCodeDraft() : request.code();
        if (code != null && code.length() > InputLimits.MAX_CODE_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "代码不能超过 " + InputLimits.MAX_CODE_LENGTH + " 字符");
        }
        String language = blankToDefault(request.language(), coding.getCodeLanguage(), DEFAULT_LANGUAGE);

        ReentrantLock chatLock = chatLocks.computeIfAbsent(coding.getId(), key -> new ReentrantLock());
        if (!chatLock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "面试官正在回复上一句,请稍等");
        }
        try {
            updateDraft(coding, code, language, CODE_MODE_FREE);

            List<InterviewMessage> history = chatHistory(coding.getId());
            int seq = nextMessageSeq(coding.getId());
            messageMapper.insert(newMessage(coding.getId(), seq, ROLE_USER, TYPE_CODING_CHAT, message));

            QuestionBank question = questionBankService.requireCodingQuestion(coding.getCodingQuestionId());

            StringBuilder buffer = new StringBuilder();
            llmService.streamCodingChat(session, question, code, language,
                    coding.getJudgeStatus(), coding.getRunStdout(), coding.getRunStderr(),
                    intValue(coding.getRunExitCode()), history, message, text -> {
                        buffer.append(text);
                        onDelta.accept(text);
                    });

            String reply = buffer.toString().strip();
            if (!reply.isEmpty()) {
                int next = nextMessageSeq(coding.getId());
                messageMapper.insert(newMessage(coding.getId(), next, ROLE_INTERVIEWER, TYPE_CODING_CHAT, reply));
            }
        } finally {
            chatLock.unlock();
        }
    }

    // ---------- 查询与工具 ----------

    private InterviewCodingView view(InterviewQuestion coding) {
        QuestionBank question = questionBankService.requireCodingQuestion(coding.getCodingQuestionId());
        InterviewCodingView.RunView latestRun = coding.getRunAt() == null ? null
                : new InterviewCodingView.RunView(
                        coding.getJudgeStatus(),
                        coding.getRunStdout(),
                        coding.getRunStderr(),
                        intValue(coding.getRunExitCode()),
                        intValue(coding.getRunRuntimeMs()),
                        format(coding.getRunAt()));
        return new InterviewCodingView(
                String.valueOf(coding.getId()),
                intValue(coding.getSeq()),
                false,
                String.valueOf(question.getId()),
                question.getTitle(),
                question.getFreeContent() != null && !question.getFreeContent().isBlank()
                        ? question.getFreeContent()
                        : freeContent(question.getContent()),
                question.getDifficulty() == null ? 2 : question.getDifficulty(),
                question.getDomain(),
                question.getKpNames() == null ? List.of() : question.getKpNames(),
                INTRO,
                format(coding.getDeadlineAt()),
                epochMillis(coding.getDeadlineAt()),
                STARTER_CODE,
                coding.getCodeDraft(),
                coding.getCodeLanguage(),
                latestRun);
    }

    private InterviewQuestion codingQuestion(long sessionId) {
        return questionMapper.selectOne(new QueryWrapper<InterviewQuestion>()
                .eq("session_id", sessionId)
                .eq("question_type", InterviewQuestionType.CODING.name())
                .orderByDesc("seq")
                .last("LIMIT 1"));
    }

    private InterviewQuestion requireCodingQuestion(long sessionId) {
        InterviewQuestion coding = codingQuestion(sessionId);
        if (coding == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前没有手撕代码环节");
        }
        return coding;
    }

    private void ensureOngoing(InterviewSession session) {
        if (intValue(session.getStatus()) != ONGOING) {
            throw new BizException(ErrorCode.CONFLICT, "这场面试已经结束");
        }
    }

    private void ensureNotExpired(InterviewQuestion coding) {
        if (coding.getDeadlineAt() != null
                && LocalDateTime.now().isAfter(coding.getDeadlineAt().plusSeconds(DEADLINE_GRACE_SECONDS))) {
            throw new BizException(ErrorCode.CONFLICT, "编程时间已结束，可以完成本题或结束面试");
        }
    }

    private void updateDraft(InterviewQuestion coding, String code, String language, String mode) {
        if (code != null && code.length() > InputLimits.MAX_CODE_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "代码不能超过 " + InputLimits.MAX_CODE_LENGTH + " 字符");
        }
        if (code != null) {
            coding.setCodeDraft(code);
        }
        if (language != null && !language.isBlank()) {
            coding.setCodeLanguage(language.strip().toUpperCase());
        }
        if (mode != null && !mode.isBlank()) {
            coding.setCodingMode(mode.strip().toUpperCase());
        }
        questionMapper.updateById(coding);
    }

    private List<InterviewMessage> chatHistory(long questionId) {
        List<InterviewMessage> all = messageMapper.selectList(new QueryWrapper<InterviewMessage>()
                .eq("question_id", questionId)
                .eq("type", TYPE_CODING_CHAT)
                .orderByAsc("seq"));
        if (all.size() <= HISTORY_LIMIT) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - HISTORY_LIMIT, all.size()));
    }

    private int nextMessageSeq(long questionId) {
        InterviewMessage last = messageMapper.selectOne(new QueryWrapper<InterviewMessage>()
                .eq("question_id", questionId)
                .orderByDesc("seq")
                .last("LIMIT 1"));
        return last == null ? 1 : intValue(last.getSeq()) + 1;
    }

    private InterviewMessage newMessage(long questionId, int seq, String role, String type, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setId(idGenerator.nextId());
        message.setQuestionId(questionId);
        message.setSeq(seq);
        message.setRole(role);
        message.setType(type);
        message.setContent(content);
        return message;
    }

    private InterviewCodingResultView resultView(InterviewSession session, InterviewQuestion coding) {
        return new InterviewCodingResultView(
                coding.getJudgeStatus() == null ? "NOT_RUN" : coding.getJudgeStatus(),
                intValue(coding.getRunExitCode()),
                coding.getRunStdout(),
                coding.getRunStderr(),
                intValue(coding.getRunRuntimeMs()),
                coding.getScore() == null ? fallbackScore(coding) : coding.getScore(),
                coding.getComment(),
                coding.getSuggestion(),
                "REPORT_READY",
                intValue(session.getCompletedQuestions()),
                intValue(session.getQuestionCount()));
    }

    private int fallbackScore(InterviewQuestion coding) {
        String status = coding.getJudgeStatus();
        if (FreeRunStatus.SUCCESS.name().equals(status)) {
            return coding.getRunStdout() != null && !coding.getRunStdout().isBlank() ? 4 : 3;
        }
        if (FreeRunStatus.RUNTIME_ERROR.name().equals(status)) {
            return 2;
        }
        if (FreeRunStatus.COMPILE_ERROR.name().equals(status)
                || FreeRunStatus.TIME_LIMIT_EXCEEDED.name().equals(status)
                || FreeRunStatus.SYSTEM_ERROR.name().equals(status)) {
            return 1;
        }
        return 2;
    }

    private String fallbackComment(InterviewQuestion coding) {
        String status = coding.getJudgeStatus();
        if (FreeRunStatus.SUCCESS.name().equals(status)) {
            return "代码成功运行，注意说明思路、时间复杂度和边界处理。";
        }
        if (FreeRunStatus.COMPILE_ERROR.name().equals(status)) {
            return "代码没有编译通过，先解决语法和类型错误。";
        }
        if (FreeRunStatus.RUNTIME_ERROR.name().equals(status)) {
            return "代码运行时报错，重点检查空指针、数组越界和递归终止条件。";
        }
        if (FreeRunStatus.TIME_LIMIT_EXCEEDED.name().equals(status)) {
            return "运行超时，检查是否存在死循环或复杂度过高。";
        }
        if (status == null) {
            return "还没有运行代码，建议先跑一组最小用例看控制台输出。";
        }
        return "运行环境异常，先按自己的思路检查代码。";
    }

    private String fallbackSuggestion(InterviewQuestion coding) {
        String status = coding.getJudgeStatus();
        if (FreeRunStatus.SUCCESS.name().equals(status)) {
            return "补充边界用例，并口述时间复杂度、空间复杂度和你做的取舍。";
        }
        if (FreeRunStatus.RUNTIME_ERROR.name().equals(status)) {
            return "在关键分支打印中间变量，先定位报错位置再修改。";
        }
        if (FreeRunStatus.COMPILE_ERROR.name().equals(status)) {
            return "先让代码编译通过，再逐步补全逻辑。";
        }
        return "先用最小用例跑通，再逐步增加边界和异常输入。";
    }

    private int pickDifficulty(InterviewSession session) {
        String text = (nullToEmpty(session.getRound()) + nullToEmpty(session.getPosition())).toLowerCase();
        if (text.contains("实习") || text.contains("校招") || text.contains("应届")) {
            return 1;
        }
        if (text.contains("社招") || text.contains("高级") || text.contains("专家")) {
            return 3;
        }
        return 2;
    }

    private Set<Long> recentCodingQuestionIds(long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(new QueryWrapper<InterviewSession>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT 10"));
        if (sessions.isEmpty()) {
            return Set.of();
        }
        List<Long> sessionIds = sessions.stream().map(InterviewSession::getId).toList();
        return questionMapper.selectList(new QueryWrapper<InterviewQuestion>()
                        .in("session_id", sessionIds)
                        .eq("question_type", InterviewQuestionType.CODING.name()))
                .stream()
                .map(InterviewQuestion::getCodingQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "结果序列化失败");
        }
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String blankToDefault(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.strip().toUpperCase();
        }
        if (second != null && !second.isBlank()) {
            return second.strip().toUpperCase();
        }
        return fallback;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(STANDARD);
    }

    private static Long epochMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 题库题面可能带 ACM/力扣模式的输入输出说明,面试 FREE_RUN 下要删掉 */
    private static String freeContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String[] lines = content.replace("\r\n", "\n").split("\n");
        StringBuilder builder = new StringBuilder();
        boolean skipSection = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s*(输入输出格式|代码要求|ACM 模式).*")) {
                skipSection = true;
                continue;
            }
            if (skipSection && trimmed.startsWith("#")) {
                skipSection = false;
            }
            if (skipSection) {
                continue;
            }
            if (trimmed.startsWith(">") && (trimmed.contains("力扣模式") || trimmed.contains("ACM 模式"))) {
                continue;
            }
            builder.append(line).append('\n');
        }
        String result = builder.toString().strip();
        if (!result.contains("开放式编码")) {
            result += "\n\n> 这是开放式编码：平台不提供测试用例，请自己构造输入、调用解法，并用控制台输出结果。";
        }
        return result;
    }

    private static Map<String, String> starterCode() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("JAVA", """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        // 在这里构造输入、调用你的解法，并用 System.out.println 输出结果
                    }
                }
                """);
        map.put("PYTHON", """
                import sys

                def main():
                    # 在这里构造输入、调用你的解法，并用 print 输出结果
                    pass

                if __name__ == "__main__":
                    main()
                """);
        return map;
    }
}
