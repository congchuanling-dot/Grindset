package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.constant.QuestionStatus;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewReport;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.service.InterviewLlmService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static com.grindset.backend.service.interview.InterviewConstants.ROLE_USER;
import static com.grindset.backend.service.interview.InterviewConstants.SESSION_FINISHED;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_FEEDBACK;
import static com.grindset.backend.service.interview.InterviewViews.intValue;

/**
 * 复盘报告:关闭会话、把未答完的题标记为放弃,再让模型汇总已完成题目。
 */
@Service
public class InterviewReportService {

    private final InterviewSessionService sessionService;
    private final InterviewSessionLocks locks;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewLlmService llmService;
    private final InterviewContextBuilder contextBuilder;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public InterviewReportService(InterviewSessionService sessionService,
                                  InterviewSessionLocks locks,
                                  InterviewSessionMapper sessionMapper,
                                  InterviewQuestionMapper questionMapper,
                                  InterviewMessageMapper messageMapper,
                                  InterviewLlmService llmService,
                                  InterviewContextBuilder contextBuilder,
                                  TransactionTemplate transactionTemplate,
                                  ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.locks = locks;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.llmService = llmService;
        this.contextBuilder = contextBuilder;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** 生成复盘报告;已生成过直接返回,支持提前结束或 0 题结束 */
    public InterviewDetail.ReportView finish(long userId, long id) {
        ReentrantLock lock = locks.lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(409, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, id);
            if (session.getReportJson() != null && !session.getReportJson().isBlank()) {
                InterviewDetail.ReportView existing = InterviewViews.parseReport(objectMapper,
                        session.getReportJson());
                if (existing != null) {
                    return existing;
                }
            }

            // 先关闭会话并把未答完的题标记为已放弃:
            // 即使报告生成失败,会话也不会一直停在"进行中",用户可以重试生成报告。
            transactionTemplate.executeWithoutResult(status -> {
                InterviewQuestion patch = new InterviewQuestion();
                patch.setStatus(QuestionStatus.ABANDONED);
                patch.setFinishedAt(LocalDateTime.now());
                questionMapper.update(patch, new QueryWrapper<InterviewQuestion>()
                        .eq("session_id", id)
                        .in("status", QuestionStatus.ASKING, QuestionStatus.ANSWERED));
                session.setStatus(SESSION_FINISHED);
                sessionMapper.updateById(session);
            });

            List<InterviewQuestion> questions = finishedQuestions(id);
            if (questions.isEmpty()) {
                // 没有完成任何题目:只结束会话,不生成无意义的复盘报告
                return null;
            }

            String resumeText = contextBuilder.resumeText(userId, session.getResumeId());
            String threadsText = threadsText(questions);
            InterviewReport report = llmService.report(session, resumeText, threadsText);

            session.setReportJson(toJson(report));
            session.setOverallScore(report.overallScore());
            sessionMapper.updateById(session);

            return toReportView(report);
        } finally {
            lock.unlock();
        }
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

    private InterviewDetail.ReportView toReportView(InterviewReport report) {
        return new InterviewDetail.ReportView(report.overallScore(), report.dimensionScores(),
                report.weakPoints(), report.highlights(), report.advice());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "结果序列化失败");
        }
    }
}
