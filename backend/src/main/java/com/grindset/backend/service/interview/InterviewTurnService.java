package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.AnswerRequest;
import com.grindset.backend.dto.AnswerResult;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewTurnDecision;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.service.InterviewLlmService;
import com.grindset.backend.service.KnowledgeProfileService;
import com.grindset.backend.service.WrongQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.grindset.backend.service.interview.InterviewConstants.QUESTION_ANSWERED;
import static com.grindset.backend.service.interview.InterviewConstants.QUESTION_DONE;
import static com.grindset.backend.service.interview.InterviewConstants.ROLE_INTERVIEWER;
import static com.grindset.backend.service.interview.InterviewConstants.ROLE_USER;
import static com.grindset.backend.service.interview.InterviewConstants.SESSION_ONGOING;
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

    private static final int MAX_ANSWER_LENGTH = 4000;
    private static final int MAX_FOLLOW_UP = 3;

    private final InterviewSessionService sessionService;
    private final InterviewSessionLocks locks;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewLlmService llmService;
    private final KnowledgeProfileService knowledgeProfileService;
    private final WrongQuestionService wrongQuestionService;
    private final InterviewContextBuilder contextBuilder;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public InterviewTurnService(InterviewSessionService sessionService,
                                InterviewSessionLocks locks,
                                InterviewSessionMapper sessionMapper,
                                InterviewQuestionMapper questionMapper,
                                InterviewMessageMapper messageMapper,
                                InterviewLlmService llmService,
                                KnowledgeProfileService knowledgeProfileService,
                                WrongQuestionService wrongQuestionService,
                                InterviewContextBuilder contextBuilder,
                                SnowflakeIdGenerator idGenerator,
                                TransactionTemplate transactionTemplate,
                                ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.locks = locks;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.llmService = llmService;
        this.knowledgeProfileService = knowledgeProfileService;
        this.wrongQuestionService = wrongQuestionService;
        this.contextBuilder = contextBuilder;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式生成下一道主问题并落库。
     * 生成过程中不写库;客户端断开则放弃本题,可重试。
     */
    public InterviewDetail.QuestionView nextQuestion(long userId, long id, Consumer<String> onDelta) {
        ReentrantLock lock = locks.lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(409, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, id);
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

            String resumeText = contextBuilder.resumeText(userId, session.getResumeId());
            List<String> covered = coveredPoints(id);
            List<KnowledgeProfileService.Candidate> candidates = weakCandidates(userId, covered);

            StringBuilder buffer = new StringBuilder();
            llmService.streamQuestion(session, resumeText, seq, covered, candidates, text -> {
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

        ReentrantLock lock = locks.lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(409, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, id);
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

            String resumeText = contextBuilder.resumeText(userId, session.getResumeId());
            List<String> knownPoints = knownPoints(userId);

            InterviewTurnDecision decision = llmService.decide(session, resumeText, question, thread,
                    knownPoints);
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
                        intValue(session.getQuestionCount()), null);
            }

            InterviewTurnDecision.Evaluation evaluation = decision.getEvaluation();
            if (evaluation == null) {
                evaluation = llmService.evaluateOnly(session, resumeText, question, thread, knownPoints);
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

            // 画像走独立事务,失败不影响已经保存的答题记录;可由管理员重建修复
            try {
                knowledgeProfileService.record(userId, id,
                        finalEvaluation.getKnowledgePoints(), finalEvaluation.getScore());
            } catch (Exception e) {
                log.error("掌握度画像更新失败 userId={} sessionId={}", userId, id, e);
            }

            // 错题收录同样是旁路:失败返回 null,不显示"已加入错题本"提示
            Long wrongQuestionId = null;
            try {
                wrongQuestionId = wrongQuestionService.collect(userId, id, question.getId());
            } catch (Exception e) {
                log.error("错题收录失败 userId={} sessionId={} questionId={}",
                        userId, id, question.getId(), e);
            }

            String nextAction = completed >= intValue(session.getQuestionCount())
                    ? "REPORT_READY" : "NEXT_QUESTION";
            return new AnswerResult(feedback, null, true,
                    new AnswerResult.Evaluation(finalEvaluation.getScore(),
                            finalEvaluation.getKnowledgePoints() == null ? List.of() : finalEvaluation.getKnowledgePoints(),
                            finalEvaluation.getComment(), finalEvaluation.getSuggestion()),
                    nextAction, completed, intValue(session.getQuestionCount()),
                    wrongQuestionId == null ? null : String.valueOf(wrongQuestionId));
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

    /** 画像候选失败时降级为原出题逻辑,不影响主流程 */
    private List<KnowledgeProfileService.Candidate> weakCandidates(long userId, List<String> covered) {
        try {
            return knowledgeProfileService.weightedCandidates(userId, covered, 5);
        } catch (Exception e) {
            log.warn("掌握度候选查询失败,按原逻辑出题 userId={}", userId, e);
            return List.of();
        }
    }

    /** 评分时让模型优先复用已有知识点名称,失败时不影响评分 */
    private List<String> knownPoints(long userId) {
        try {
            return knowledgeProfileService.knownNames(userId, 30);
        } catch (Exception e) {
            log.warn("已有知识点查询失败,本次评分不复用名称 userId={}", userId, e);
            return List.of();
        }
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
            throw new BizException(500, "结果序列化失败");
        }
    }
}
