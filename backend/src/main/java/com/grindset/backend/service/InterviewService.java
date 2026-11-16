package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.AnswerRequest;
import com.grindset.backend.dto.AnswerResult;
import com.grindset.backend.dto.CreateInterviewRequest;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewReport;
import com.grindset.backend.dto.InterviewSummary;
import com.grindset.backend.dto.InterviewTurnDecision;
import com.grindset.backend.dto.ResumeDetail;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.entity.Resume;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.mapper.ResumeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * AI 面试业务:会话生命周期、题目线程状态流转、答题落库与复盘报告。
 * 一道"大题" = 主问题 + 1~3 次追问,题与题之间不共享对话上下文。
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> STYLES = Set.of("pressure", "middle", "relaxed");
    private static final Set<Integer> QUESTION_COUNTS = Set.of(3, 5, 8);
    private static final int MAX_ANSWER_LENGTH = 4000;
    private static final int MAX_FOCUS = 4;
    private static final int MAX_FOLLOW_UP = 3;

    private static final int SESSION_ONGOING = 0;
    private static final int SESSION_FINISHED = 1;
    private static final int QUESTION_ANSWERED = 2;
    private static final int QUESTION_DONE = 3;

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String ROLE_USER = "USER";
    private static final String TYPE_QUESTION = "QUESTION";
    private static final String TYPE_FOLLOW_UP = "FOLLOW_UP";
    private static final String TYPE_ANSWER = "ANSWER";
    private static final String TYPE_FEEDBACK = "FEEDBACK";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeService resumeService;
    private final InterviewLlmService llmService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** 单实例内按会话串行化,防止连点造成重复出题/重复答题 */
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public InterviewService(InterviewSessionMapper sessionMapper, InterviewQuestionMapper questionMapper,
                            InterviewMessageMapper messageMapper, ResumeMapper resumeMapper,
                            ResumeService resumeService, InterviewLlmService llmService,
                            SnowflakeIdGenerator idGenerator, TransactionTemplate transactionTemplate,
                            ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.resumeMapper = resumeMapper;
        this.resumeService = resumeService;
        this.llmService = llmService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** 创建会话:校验简历归属与解析状态,不在这里出题 */
    public InterviewDetail create(long userId, CreateInterviewRequest request) {
        if (request == null) {
            throw new BizException(400, "请求不能为空");
        }
        long resumeId = parseId(request.getResumeId(), "简历 ID 不合法");

        String position = request.getPosition() == null ? "" : request.getPosition().strip();
        if (position.isEmpty()) {
            throw new BizException(400, "请选择面试岗位");
        }
        if (position.length() > 64) {
            throw new BizException(400, "面试岗位过长");
        }

        String style = request.getStyle() == null ? "" : request.getStyle().strip();
        if (!STYLES.contains(style)) {
            throw new BizException(400, "面试风格不合法");
        }

        String round = request.getRound() == null ? null : request.getRound().strip();
        if (round != null && round.isEmpty()) {
            round = null;
        }
        if (round != null && round.length() > 32) {
            throw new BizException(400, "面试轮次过长");
        }

        List<String> focus = normalizeFocus(request.getFocus());

        int questionCount = request.getQuestionCount() == null ? 5 : request.getQuestionCount();
        if (!QUESTION_COUNTS.contains(questionCount)) {
            throw new BizException(400, "题数只能是 3 / 5 / 8");
        }

        ResumeDetail resume = resumeService.get(userId, resumeId);
        if (resume.parseStatus() != 1) {
            throw new BizException(400, "这份简历还没有解析成功,请先在简历管理页重新上传");
        }

        long sessionId = idGenerator.nextId();
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setResumeId(resumeId);
        session.setPosition(position);
        session.setStyle(style);
        session.setRound(round);
        session.setFocus(focus);
        session.setQuestionCount(questionCount);
        session.setCompletedQuestions(0);
        session.setStatus(SESSION_ONGOING);
        sessionMapper.insert(session);

        return detail(userId, sessionId);
    }

    /** 历史会话列表,最新 50 条 */
    public List<InterviewSummary> list(long userId) {
        List<InterviewSession> sessions = sessionMapper.selectList(new QueryWrapper<InterviewSession>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT 50"));
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<Long> resumeIds = sessions.stream().map(InterviewSession::getResumeId).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        for (Resume resume : resumeMapper.selectBatchIds(resumeIds)) {
            names.put(resume.getId(), resume.getFileName());
        }

        List<InterviewSummary> result = new ArrayList<>(sessions.size());
        for (InterviewSession session : sessions) {
            result.add(new InterviewSummary(String.valueOf(session.getId()),
                    names.getOrDefault(session.getResumeId(), "已删除的简历"),
                    session.getPosition(), session.getStyle(), session.getRound(),
                    intValue(session.getQuestionCount()), intValue(session.getCompletedQuestions()),
                    intValue(session.getStatus()), session.getOverallScore(), format(session.getCreatedAt())));
        }
        return result;
    }

    /** 会话详情,刷新页面后靠它恢复全部线程 */
    public InterviewDetail detail(long userId, long id) {
        InterviewSession session = requireOwned(userId, id);
        return buildDetail(session);
    }

    /**
     * 流式生成下一道主问题并落库。
     * 生成过程中不写库;客户端断开则放弃本题,可重试。
     */
    public InterviewDetail.QuestionView nextQuestion(long userId, long id, Consumer<String> onDelta) {
        ReentrantLock lock = lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(409, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = requireOwned(userId, id);
            if (intValue(session.getStatus()) != SESSION_ONGOING) {
                throw new BizException(409, "这场面试已经结束");
            }

            InterviewQuestion latest = latestQuestion(id);
            if (latest != null && intValue(latest.getStatus()) != QUESTION_DONE) {
                throw new BizException(409, "请先回答当前题目");
            }

            int seq = intValue(session.getCompletedQuestions()) + 1;
            if (seq > intValue(session.getQuestionCount())) {
                throw new BizException(400, "题目已全部答完,请生成复盘报告");
            }

            String resumeText = resumeText(userId, session.getResumeId());
            List<String> covered = coveredPoints(id);

            StringBuilder buffer = new StringBuilder();
            llmService.streamQuestion(session, resumeText, seq, covered, text -> {
                buffer.append(text);
                onDelta.accept(text);
            });

            String questionText = buffer.toString().strip();
            if (questionText.isEmpty()) {
                throw new BizException(502, "AI 没有生成有效题目,请重试");
            }

            long questionId = idGenerator.nextId();
            InterviewQuestion question = new InterviewQuestion();
            question.setId(questionId);
            question.setSessionId(id);
            question.setSeq(seq);
            question.setStatus(QUESTION_ANSWERED);
            question.setFollowUpCount(0);
            questionMapper.insert(question);

            InterviewMessage message = newMessage(questionId, 1, ROLE_INTERVIEWER, TYPE_QUESTION, questionText);
            messageMapper.insert(message);

            return new InterviewDetail.QuestionView(String.valueOf(questionId), seq, QUESTION_ANSWERED,
                    0, null, List.of(), null, null, List.of(toView(message)));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 提交一次回答:模型给一句话点评,并决定追问还是收束本题。
     * 一次请求完成,前端不需要额外拉取。
     */
    public AnswerResult answer(long userId, long id, AnswerRequest request) {
        String content = request == null || request.getContent() == null ? "" : request.getContent().strip();
        if (content.isEmpty()) {
            throw new BizException(400, "回答不能为空");
        }
        if (content.length() > MAX_ANSWER_LENGTH) {
            throw new BizException(400, "回答不能超过 " + MAX_ANSWER_LENGTH + " 字");
        }

        ReentrantLock lock = lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(409, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = requireOwned(userId, id);
            if (intValue(session.getStatus()) != SESSION_ONGOING) {
                throw new BizException(409, "这场面试已经结束");
            }

            InterviewQuestion question = latestQuestion(id);
            if (question == null || intValue(question.getStatus()) != QUESTION_ANSWERED) {
                throw new BizException(409, "当前没有待回答的问题");
            }

            // 本次回答还没落库,但必须带进上下文,否则模型看不到候选人刚说的话
            List<InterviewMessage> thread = new ArrayList<>(threadOf(question.getId()));
            InterviewMessage userMessage = newMessage(question.getId(), 0, ROLE_USER, TYPE_ANSWER, content);
            thread.add(userMessage);

            String resumeText = resumeText(userId, session.getResumeId());

            InterviewTurnDecision decision = llmService.decide(session, resumeText, question, thread);
            String feedback = decision.getFeedback();
            int followUpCount = intValue(question.getFollowUpCount());

            boolean followUp = Boolean.TRUE.equals(decision.getShouldFollowUp())
                    && followUpCount < MAX_FOLLOW_UP
                    && decision.getFollowUpQuestion() != null
                    && !decision.getFollowUpQuestion().isBlank();

            if (followUp) {
                String followUpQuestion = decision.getFollowUpQuestion().strip();
                transactionTemplate.executeWithoutResult(status -> {
                    int seq = nextMessageSeq(question.getId());
                    messageMapper.insert(newMessage(question.getId(), seq++, ROLE_USER,
                            TYPE_ANSWER, content));
                    if (feedback != null && !feedback.isBlank()) {
                        messageMapper.insert(newMessage(question.getId(), seq++, ROLE_INTERVIEWER,
                                TYPE_FEEDBACK, feedback));
                    }
                    messageMapper.insert(newMessage(question.getId(), seq, ROLE_INTERVIEWER,
                            TYPE_FOLLOW_UP, followUpQuestion));
                    question.setFollowUpCount(followUpCount + 1);
                    questionMapper.updateById(question);
                });
                return new AnswerResult(feedback, followUpQuestion, false, null,
                        "ANSWER_FOLLOW_UP", intValue(session.getCompletedQuestions()),
                        intValue(session.getQuestionCount()));
            }

            InterviewTurnDecision.Evaluation evaluation = decision.getEvaluation();
            if (evaluation == null) {
                evaluation = llmService.evaluateOnly(session, resumeText, question, thread);
            }
            InterviewTurnDecision.Evaluation finalEvaluation = evaluation;
            int completed = intValue(session.getCompletedQuestions()) + 1;
            String evaluationJson = toJson(finalEvaluation);

            transactionTemplate.executeWithoutResult(status -> {
                int seq = nextMessageSeq(question.getId());
                messageMapper.insert(newMessage(question.getId(), seq++, ROLE_USER,
                        TYPE_ANSWER, content));
                if (feedback != null && !feedback.isBlank()) {
                    messageMapper.insert(newMessage(question.getId(), seq, ROLE_INTERVIEWER,
                            TYPE_FEEDBACK, feedback));
                }
                question.setStatus(QUESTION_DONE);
                question.setScore(finalEvaluation.getScore());
                question.setKnowledgePoints(finalEvaluation.getKnowledgePoints());
                question.setComment(finalEvaluation.getComment());
                question.setSuggestion(finalEvaluation.getSuggestion());
                question.setEvaluationJson(evaluationJson);
                question.setFinishedAt(LocalDateTime.now());
                questionMapper.updateById(question);

                session.setCompletedQuestions(completed);
                sessionMapper.updateById(session);
            });

            String nextAction = completed >= intValue(session.getQuestionCount())
                    ? "REPORT_READY" : "NEXT_QUESTION";
            return new AnswerResult(feedback, null, true,
                    new AnswerResult.Evaluation(finalEvaluation.getScore(),
                            finalEvaluation.getKnowledgePoints() == null ? List.of() : finalEvaluation.getKnowledgePoints(),
                            finalEvaluation.getComment(), finalEvaluation.getSuggestion()),
                    nextAction, completed, intValue(session.getQuestionCount()));
        } finally {
            lock.unlock();
        }
    }

    /** 生成复盘报告;已生成过直接返回,支持提前结束 */
    public InterviewDetail.ReportView finish(long userId, long id) {
        ReentrantLock lock = lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(409, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = requireOwned(userId, id);
            if (intValue(session.getStatus()) == SESSION_FINISHED && session.getReportJson() != null) {
                return parseReport(session.getReportJson());
            }

            List<InterviewQuestion> questions = finishedQuestions(id);
            if (questions.isEmpty()) {
                throw new BizException(400, "还没有完成任何题目,无法生成复盘报告");
            }

            String resumeText = resumeText(userId, session.getResumeId());
            String threadsText = threadsText(questions);
            InterviewReport report = llmService.report(session, resumeText, threadsText);

            session.setReportJson(toJson(report));
            session.setOverallScore(report.overallScore());
            session.setStatus(SESSION_FINISHED);
            sessionMapper.updateById(session);

            return toReportView(report);
        } finally {
            lock.unlock();
        }
    }

    /** 删除会话及全部线程消息 */
    public void delete(long userId, long id) {
        InterviewSession session = requireOwned(userId, id);
        transactionTemplate.executeWithoutResult(status -> {
            List<InterviewQuestion> questions = questionMapper.selectList(
                    new QueryWrapper<InterviewQuestion>().eq("session_id", id));
            if (!questions.isEmpty()) {
                List<Long> questionIds = questions.stream().map(InterviewQuestion::getId).toList();
                messageMapper.delete(new QueryWrapper<InterviewMessage>().in("question_id", questionIds));
                questionMapper.delete(new QueryWrapper<InterviewQuestion>().eq("session_id", id));
            }
            sessionMapper.deleteById(session.getId());
        });
    }

    // ---------- 内部方法 ----------

    private InterviewDetail buildDetail(InterviewSession session) {
        Resume resume = resumeMapper.selectById(session.getResumeId());
        String resumeFileName = resume == null ? "已删除的简历" : resume.getFileName();

        List<InterviewQuestion> questions = questionMapper.selectList(new QueryWrapper<InterviewQuestion>()
                .eq("session_id", session.getId())
                .orderByAsc("seq"));
        Map<Long, List<InterviewMessage>> messagesByQuestion = new LinkedHashMap<>();
        if (!questions.isEmpty()) {
            List<Long> questionIds = questions.stream().map(InterviewQuestion::getId).toList();
            List<InterviewMessage> messages = messageMapper.selectList(new QueryWrapper<InterviewMessage>()
                    .in("question_id", questionIds)
                    .orderByAsc("seq"));
            for (InterviewMessage message : messages) {
                messagesByQuestion.computeIfAbsent(message.getQuestionId(), key -> new ArrayList<>()).add(message);
            }
        }

        List<InterviewDetail.QuestionView> questionViews = new ArrayList<>(questions.size());
        for (InterviewQuestion question : questions) {
            List<InterviewDetail.MessageView> messages = messagesByQuestion
                    .getOrDefault(question.getId(), List.of()).stream()
                    .map(this::toView)
                    .toList();
            questionViews.add(new InterviewDetail.QuestionView(String.valueOf(question.getId()),
                    intValue(question.getSeq()), intValue(question.getStatus()),
                    intValue(question.getFollowUpCount()), question.getScore(),
                    question.getKnowledgePoints() == null ? List.of() : question.getKnowledgePoints(),
                    question.getComment(), question.getSuggestion(), messages));
        }

        InterviewDetail.ReportView report = null;
        if (session.getReportJson() != null && !session.getReportJson().isBlank()) {
            report = parseReport(session.getReportJson());
        }

        return new InterviewDetail(String.valueOf(session.getId()), String.valueOf(session.getResumeId()),
                resumeFileName, session.getPosition(), session.getStyle(), session.getRound(),
                session.getFocus() == null ? List.of() : session.getFocus(),
                intValue(session.getQuestionCount()), intValue(session.getCompletedQuestions()),
                intValue(session.getStatus()), session.getOverallScore(),
                format(session.getCreatedAt()), questionViews, report);
    }

    private InterviewDetail.ReportView parseReport(String json) {
        try {
            return objectMapper.readValue(json, InterviewDetail.ReportView.class);
        } catch (JsonProcessingException e) {
            log.warn("复盘报告 JSON 解析失败", e);
            return null;
        }
    }

    private InterviewDetail.ReportView toReportView(InterviewReport report) {
        return new InterviewDetail.ReportView(report.overallScore(), report.dimensionScores(),
                report.weakPoints(), report.highlights(), report.advice());
    }

    private InterviewSession requireOwned(long userId, long id) {
        InterviewSession session = sessionMapper.selectById(id);
        if (session == null || session.getUserId() == null || session.getUserId() != userId) {
            throw new BizException(404, "面试会话不存在");
        }
        return session;
    }

    private InterviewQuestion latestQuestion(long sessionId) {
        return questionMapper.selectOne(new QueryWrapper<InterviewQuestion>()
                .eq("session_id", sessionId)
                .orderByDesc("seq")
                .last("LIMIT 1"));
    }

    private List<InterviewQuestion> finishedQuestions(long sessionId) {
        return questionMapper.selectList(new QueryWrapper<InterviewQuestion>()
                .eq("session_id", sessionId)
                .eq("status", QUESTION_DONE)
                .orderByAsc("seq"));
    }

    private List<InterviewMessage> threadOf(long questionId) {
        return messageMapper.selectList(new QueryWrapper<InterviewMessage>()
                .eq("question_id", questionId)
                .orderByAsc("seq"));
    }

    private List<String> coveredPoints(long sessionId) {
        List<InterviewQuestion> questions = finishedQuestions(sessionId);
        List<String> points = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            if (question.getKnowledgePoints() != null) {
                for (String point : question.getKnowledgePoints()) {
                    if (point != null && !point.isBlank() && !points.contains(point)) {
                        points.add(point);
                    }
                }
            }
        }
        return points;
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

    private InterviewDetail.MessageView toView(InterviewMessage message) {
        return new InterviewDetail.MessageView(String.valueOf(message.getId()), intValue(message.getSeq()),
                message.getRole(), message.getType(), message.getContent(), format(message.getCreatedAt()));
    }

    /** 把全部已完成题目拼成给复盘模型看的文本 */
    private String threadsText(List<InterviewQuestion> questions) {
        StringBuilder builder = new StringBuilder();
        for (InterviewQuestion question : questions) {
            builder.append("第 ").append(question.getSeq()).append(" 题");
            if (question.getScore() != null) {
                builder.append("(本题评分 ").append(question.getScore()).append("/5");
                if (question.getKnowledgePoints() != null && !question.getKnowledgePoints().isEmpty()) {
                    builder.append(",知识点:").append(String.join("、", question.getKnowledgePoints()));
                }
                builder.append(")");
            }
            builder.append('\n');
            for (InterviewMessage message : threadOf(question.getId())) {
                if (TYPE_FEEDBACK.equals(message.getType())) {
                    continue;
                }
                String speaker = ROLE_USER.equals(message.getRole()) ? "候选人" : "面试官";
                builder.append(speaker).append(':').append(message.getContent()).append('\n');
            }
            if (question.getComment() != null) {
                builder.append("面试官总评:").append(question.getComment()).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString().strip();
    }

    /** 简历全量结构化内容,不截断 */
    private String resumeText(long userId, long resumeId) {
        ResumeDetail resume;
        try {
            resume = resumeService.get(userId, resumeId);
        } catch (BizException e) {
            throw new BizException(409, "使用的简历已被删除,无法继续面试");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("姓名:").append(resume.candidateName() == null ? "未识别" : resume.candidateName())
                .append('\n');
        builder.append("候选人类型:").append(candidateTypeLabel(resume.candidateType())).append('\n');

        builder.append("技能清单:\n");
        if (resume.skills().isEmpty()) {
            builder.append("- 无\n");
        }
        for (ResumeDetail.SkillView skill : resume.skills()) {
            builder.append("- ").append(skill.name());
            List<String> meta = new ArrayList<>();
            if (skill.category() != null && !skill.category().isBlank()) {
                meta.add(skill.category());
            }
            if (skill.proficiency() != null) {
                meta.add(skill.proficiency() + "/5");
            }
            if (!meta.isEmpty()) {
                builder.append("(").append(String.join("、", meta)).append(")");
            }
            if (skill.evidence() != null && !skill.evidence().isBlank()) {
                builder.append(":").append(skill.evidence());
            }
            builder.append('\n');
        }

        builder.append("\n经历与章节:\n");
        for (ResumeDetail.SectionView section : resume.sections()) {
            builder.append("## ").append(section.title()).append('\n');
            if (section.items().isEmpty()) {
                builder.append("- 无条目\n");
            }
            for (ResumeDetail.ItemView item : section.items()) {
                builder.append("- ");
                builder.append(item.title() == null ? "未命名" : item.title());
                if (item.subtitle() != null && !item.subtitle().isBlank()) {
                    builder.append(" · ").append(item.subtitle());
                }
                String time = timeRange(item.startDate(), item.endDate());
                if (!time.isEmpty()) {
                    builder.append("(").append(time).append(")");
                }
                builder.append('\n');
                if (item.description() != null && !item.description().isBlank()) {
                    builder.append("  ").append(item.description()).append('\n');
                }
                if (item.tags() != null && !item.tags().isEmpty()) {
                    builder.append("  标签:").append(String.join("、", item.tags())).append('\n');
                }
            }
        }
        return builder.toString().strip();
    }

    private static String candidateTypeLabel(String type) {
        if ("FRESH_GRADUATE".equals(type)) {
            return "应届生";
        }
        if ("INTERN".equals(type)) {
            return "找实习";
        }
        if ("EXPERIENCED".equals(type)) {
            return "社招";
        }
        return "未识别";
    }

    private static String timeRange(String start, String end) {
        String from = start == null ? "" : start.strip();
        String to = end == null ? "" : end.strip();
        if (from.isEmpty() && to.isEmpty()) {
            return "";
        }
        if (from.isEmpty()) {
            return to;
        }
        if (to.isEmpty()) {
            return from;
        }
        return from + " - " + to;
    }

    private List<String> normalizeFocus(List<String> focus) {
        List<String> result = new ArrayList<>();
        if (focus == null) {
            return result;
        }
        for (String item : focus) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (result.size() >= MAX_FOCUS) {
                throw new BizException(400, "侧重方向最多选 " + MAX_FOCUS + " 个");
            }
            String value = item.strip();
            if (value.length() > 32) {
                throw new BizException(400, "侧重方向过长");
            }
            result.add(value);
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "结果序列化失败");
        }
    }

    private ReentrantLock lockOf(long sessionId) {
        return locks.computeIfAbsent(sessionId, key -> new ReentrantLock());
    }

    private static long parseId(String value, String message) {
        try {
            return Long.parseLong(value == null ? "" : value.strip());
        } catch (NumberFormatException e) {
            throw new BizException(400, message);
        }
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FORMAT);
    }
}
