package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.InterviewQuestionType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.constant.QuestionStatus;
import com.grindset.backend.dto.AnswerRequest;
import com.grindset.backend.dto.AnswerResult;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewTurnDecision;
import com.grindset.backend.dto.JdProfile;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.service.InterviewLlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.grindset.backend.common.constant.InputLimits.MAX_ANSWER_LENGTH;
import static com.grindset.backend.service.interview.InterviewConstants.MAX_FOLLOW_UP;
import static com.grindset.backend.service.interview.InterviewConstants.ROLE_INTERVIEWER;
import static com.grindset.backend.service.interview.InterviewConstants.ROLE_USER;
import static com.grindset.backend.common.constant.InterviewSessionStatus.ONGOING;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_ANSWER;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_FEEDBACK;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_FOLLOW_UP;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_QUESTION;
import static com.grindset.backend.service.interview.InterviewViews.intValue;
import static com.grindset.backend.service.interview.InterviewViews.toView;

/**
 * 出题与答题:一道大题 = 主问题 + 最多 3 次追问,题与题之间不共享对话上下文。
 */
@Service
public class InterviewTurnService {

    private static final Logger log = LoggerFactory.getLogger(InterviewTurnService.class);

    private final InterviewSessionService sessionService;
    private final InterviewSessionLocks locks;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewLlmService llmService;
    private final InterviewContextBuilder contextBuilder;
    private final InterviewJdService jdService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public InterviewTurnService(InterviewSessionService sessionService,
                                InterviewSessionLocks locks,
                                InterviewSessionMapper sessionMapper,
                                InterviewQuestionMapper questionMapper,
                                InterviewMessageMapper messageMapper,
                                InterviewLlmService llmService,
                                InterviewContextBuilder contextBuilder,
                                InterviewJdService jdService,
                                SnowflakeIdGenerator idGenerator,
                                TransactionTemplate transactionTemplate,
                                ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.locks = locks;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.llmService = llmService;
        this.contextBuilder = contextBuilder;
        this.jdService = jdService;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式生成下一道主问题并落库。
     * 生成过程中不写库;客户端断开则放弃本题,可重试。
     */
    public InterviewDetail.QuestionView nextQuestion(long userId, long id, Consumer<String> onDelta) {
        //todo:这里是可重入锁，后续改成分布式锁
        ReentrantLock lock = locks.lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, id);
            if (intValue(session.getStatus()) != ONGOING) {
                throw new BizException(ErrorCode.CONFLICT, "这场面试已经结束");
            }

            InterviewQuestion latest = latestQuestion(id);
            if (latest != null && intValue(latest.getStatus()) != QuestionStatus.DONE) {
                throw new BizException(ErrorCode.CONFLICT, "请先回答当前题目");
            }

            int seq = intValue(session.getCompletedQuestions()) + 1;
            if (seq > intValue(session.getQuestionCount())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "题目已全部答完,请生成复盘报告");
            }
            if (intValue(session.getCodingEnabled()) == 1 && seq == intValue(session.getQuestionCount())) {
                throw new BizException(ErrorCode.CONFLICT, "请进入手撕代码环节");
            }
            String resumeText = contextBuilder.resumeText(userId, session.getResumeId());
            //todo：按已经出过的题目去重，后续要改
            List<String> covered = coveredPoints(id);
            boolean hrRound = isHrRound(session);
            JdProfile jdProfile = hrRound ? null : jdService.profileOf(session);
            int verbalCount = intValue(session.getQuestionCount())
                    - (intValue(session.getCodingEnabled()) == 1 ? 1 : 0);
            String competency = hrRound ? null
                    : InterviewJdPlan.competencyFor(jdProfile, seq, verbalCount);

            StringBuilder buffer = new StringBuilder();
            llmService.streamQuestion(session, resumeText, seq, covered, jdProfile, competency,
                    text -> {
                        buffer.append(text);
                        onDelta.accept(text);
                    });

            String questionText = buffer.toString().strip();
            if (questionText.isEmpty()) {
                throw new BizException(ErrorCode.BAD_GATEWAY, "AI 没有生成有效题目,请重试");
            }

            long questionId = idGenerator.nextId();
            InterviewQuestion question = new InterviewQuestion();
            question.setId(questionId);
            question.setSessionId(id);
            question.setSeq(seq);
            question.setQuestionType(InterviewQuestionType.VERBAL);
            question.setStatus(QuestionStatus.ANSWERED);
            question.setFollowUpCount(0);
            question.setCompetency(competency);
            questionMapper.insert(question);

            InterviewMessage message = newMessage(questionId, 1, ROLE_INTERVIEWER, TYPE_QUESTION, questionText);
            messageMapper.insert(message);

            return new InterviewDetail.QuestionView(String.valueOf(questionId), seq,
                    InterviewQuestionType.VERBAL.name(),
                    QuestionStatus.ANSWERED, 0, null, List.of(), competency, null,
                    null, null, null, null, null, null, null, null, null, List.of(toView(message)));
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
            throw new BizException(ErrorCode.BAD_REQUEST, "回答不能为空");
        }
        if (content.length() > MAX_ANSWER_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回答不能超过 " + MAX_ANSWER_LENGTH + " 字");
        }

        ReentrantLock lock = locks.lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, id);
            if (intValue(session.getStatus()) != ONGOING) {
                throw new BizException(ErrorCode.CONFLICT, "这场面试已经结束");
            }

            InterviewQuestion question = latestQuestion(id);
            if (question == null || intValue(question.getStatus()) != QuestionStatus.ANSWERED) {
                throw new BizException(ErrorCode.CONFLICT, "当前没有待回答的问题");
            }
            if (question.getQuestionType() == InterviewQuestionType.CODING) {
                throw new BizException(ErrorCode.CONFLICT, "当前是编程题，请提交代码");
            }

            // 本次回答还没落库,但必须带进上下文,否则模型看不到候选人刚说的话
            List<InterviewMessage> thread = new ArrayList<>(threadOf(question.getId()));
            InterviewMessage userMessage = newMessage(question.getId(), 0, ROLE_USER, TYPE_ANSWER, content);
            thread.add(userMessage);

            String resumeText = contextBuilder.resumeText(userId, session.getResumeId());
            JdProfile jdProfile = isHrRound(session) ? null : jdService.profileOf(session);

            InterviewTurnDecision decision = llmService.decide(session, resumeText, question, thread,
                    jdProfile);
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
                evaluation = llmService.evaluateOnly(session, resumeText, question, thread, jdProfile);
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
                question.setStatus(QuestionStatus.DONE);
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

            // 评分已落库,画像/错题相关旁路已下线
            boolean codingPending = intValue(session.getCodingEnabled()) == 1
                    && completed == intValue(session.getQuestionCount()) - 1;
            String nextAction = completed >= intValue(session.getQuestionCount())
                    ? "REPORT_READY"
                    : codingPending ? "CODING_READY" : "NEXT_QUESTION";
            return new AnswerResult(feedback, null, true,
                    new AnswerResult.Evaluation(finalEvaluation.getScore(),
                            finalEvaluation.getKnowledgePoints() == null ? List.of() : finalEvaluation.getKnowledgePoints(),
                            finalEvaluation.getComment(), finalEvaluation.getSuggestion()),
                    nextAction, completed, intValue(session.getQuestionCount()));
        } finally {
            lock.unlock();
        }
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
                .eq("status", QuestionStatus.DONE)
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "结果序列化失败");
        }
    }

    /** HR 面只考察行为与动机,不参与 JD 技术能力项分配 */
    private static boolean isHrRound(InterviewSession session) {
        return session.getRound() != null && session.getRound().contains("HR");
    }
}
