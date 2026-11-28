package com.grindset.backend.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.InterviewQuestionType;
import com.grindset.backend.common.Texts;
import com.grindset.backend.common.constant.QuestionStatus;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewReport;
import com.grindset.backend.dto.JdProfile;
import com.grindset.backend.dto.WeaknessView;
import com.grindset.backend.entity.InterviewMessage;
import com.grindset.backend.entity.InterviewQuestion;
import com.grindset.backend.entity.InterviewSession;
import com.grindset.backend.mapper.InterviewMessageMapper;
import com.grindset.backend.mapper.InterviewQuestionMapper;
import com.grindset.backend.mapper.InterviewSessionMapper;
import com.grindset.backend.service.InterviewLlmService;
import com.grindset.backend.service.training.WeaknessDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.grindset.backend.service.interview.InterviewConstants.ROLE_USER;
import static com.grindset.backend.common.constant.InterviewSessionStatus.FINISHED;
import static com.grindset.backend.service.interview.InterviewConstants.TYPE_FEEDBACK;
import static com.grindset.backend.service.interview.InterviewViews.intValue;

/**
 * 复盘报告:关闭会话、把未答完的题标记为放弃,再让模型汇总已完成题目。
 */
@Service
public class InterviewReportService {

    private static final Logger log = LoggerFactory.getLogger(InterviewReportService.class);

    private final InterviewSessionService sessionService;
    private final InterviewSessionLocks locks;
    private final InterviewCodingService codingService;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewLlmService llmService;
    private final InterviewContextBuilder contextBuilder;
    private final InterviewJdService jdService;
    private final WeaknessDiagnosisService weaknessDiagnosisService;
    private final InterviewWeaknessViewAugmenter weaknessViewAugmenter;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public InterviewReportService(InterviewSessionService sessionService,
                                  InterviewSessionLocks locks,
                                  InterviewCodingService codingService,
                                  InterviewSessionMapper sessionMapper,
                                  InterviewQuestionMapper questionMapper,
                                  InterviewMessageMapper messageMapper,
                                  InterviewLlmService llmService,
                                  InterviewContextBuilder contextBuilder,
                                  InterviewJdService jdService,
                                  WeaknessDiagnosisService weaknessDiagnosisService,
                                  InterviewWeaknessViewAugmenter weaknessViewAugmenter,
                                  TransactionTemplate transactionTemplate,
                                  ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.locks = locks;
        this.codingService = codingService;
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.messageMapper = messageMapper;
        this.llmService = llmService;
        this.contextBuilder = contextBuilder;
        this.jdService = jdService;
        this.weaknessDiagnosisService = weaknessDiagnosisService;
        this.weaknessViewAugmenter = weaknessViewAugmenter;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** 生成复盘报告;已生成过直接返回,支持提前结束或 0 题结束 */
    public InterviewDetail.ReportView finish(long userId, long id) {
        //todo:这里是可重入锁，后续改成分布式锁
        ReentrantLock lock = locks.lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "当前会话正在处理中,请稍后再试");
        }
        try {
            InterviewSession session = sessionService.requireOwned(userId, id);
            if (session.getReportJson() != null && !session.getReportJson().isBlank()) {
                InterviewDetail.ReportView existing = InterviewViews.parseReport(objectMapper,
                        session.getReportJson());
                if (existing != null) {
                    return weaknessViewAugmenter.augment(userId, id, existing, finishedQuestions(id));
                }
            }

            // 用户交卷/关页面时可能已经提交过代码但没点“完成本题”,复盘前自动结算
            codingService.completeLatest(userId, id);
            InterviewSession current = sessionService.requireOwned(userId, id);

            // 先关闭会话并把未答完的题标记为已放弃:
            // 即使报告生成失败,会话也不会一直停在"进行中",用户可以重试生成报告。
            transactionTemplate.executeWithoutResult(status -> {
                InterviewQuestion patch = new InterviewQuestion();
                patch.setStatus(QuestionStatus.ABANDONED);
                patch.setFinishedAt(LocalDateTime.now());
                questionMapper.update(patch, new QueryWrapper<InterviewQuestion>()
                        .eq("session_id", id)
                        .in("status", QuestionStatus.ASKING, QuestionStatus.ANSWERED));
                current.setStatus(FINISHED);
                sessionMapper.updateById(current);
            });

            List<InterviewQuestion> questions = finishedQuestions(id);
            if (questions.isEmpty()) {
                // 没有完成任何题目:只结束会话,不生成无意义的复盘报告
                return null;
            }

            boolean hasCoding = questions.stream()
                    .anyMatch(q -> q.getQuestionType() == InterviewQuestionType.CODING);

            String resumeText = contextBuilder.resumeText(userId, current.getResumeId());
            String threadsText = threadsText(questions);
            JdProfile jdProfile = isHrRound(current) ? null : jdService.profileOf(current);
            InterviewReport report = llmService.report(current, resumeText, threadsText, hasCoding, jdProfile);

            List<WeaknessView> weaknesses;
            try {
                weaknesses = weaknessDiagnosisService.resolveAndPersist(
                        userId, id, questions, report, lastAnswers(questions));
            } catch (Exception e) {
                log.warn("弱项落库失败,降级为内存诊断 sessionId={}", id, e);
                weaknesses = weaknessDiagnosisService.diagnoseFromQuestions(questions, lastAnswers(questions));
            }

            InterviewDetail.ReportView reportView = toReportView(report, questions, jdProfile, weaknesses);
            current.setReportJson(toJson(reportView));
            current.setOverallScore(report.overallScore());
            sessionMapper.updateById(current);

            return reportView;
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
            if (question.getQuestionType() == InterviewQuestionType.CODING) {
                appendCoding(builder, question);
                continue;
            }
            builder.append("第 ").append(question.getSeq()).append(" 题");
            if (question.getScore() != null) {
                builder.append("(本题评分 ").append(question.getScore()).append("/5");
                if (question.getKnowledgePoints() != null && !question.getKnowledgePoints().isEmpty()) {
                    builder.append(",知识点:").append(String.join("、", question.getKnowledgePoints()));
                }
                builder.append(")");
            }
            if (question.getCompetency() != null && !question.getCompetency().isBlank()) {
                builder.append(" [能力项:").append(question.getCompetency()).append(']');
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

    /** 手撕代码题单独成段,把判题结果和最终代码给复盘模型 */
    private void appendCoding(StringBuilder builder, InterviewQuestion question) {
        builder.append("第 ").append(question.getSeq()).append(" 题（手撕代码）");
        if (question.getCodingTitle() != null && !question.getCodingTitle().isBlank()) {
            builder.append("：").append(question.getCodingTitle());
        }
        if (question.getScore() != null) {
            builder.append("(本题评分 ").append(question.getScore()).append("/5)");
        }
        builder.append('\n');

        if (question.getRunAt() == null) {
            builder.append("运行结果:未运行代码\n");
        } else {
            builder.append("运行结果:")
                    .append(question.getJudgeStatus() == null ? "UNKNOWN" : question.getJudgeStatus())
                    .append(",退出码 ").append(intValue(question.getRunExitCode()));
            if (question.getRunRuntimeMs() != null) {
                builder.append(",耗时 ").append(question.getRunRuntimeMs()).append("ms");
            }
            builder.append('\n');
            if (question.getRunStdout() != null && !question.getRunStdout().isBlank()) {
                builder.append("标准输出:\n<<<\n")
                        .append(Texts.truncate(question.getRunStdout(), 4000))
                        .append("\n>>>\n");
            }
            if (question.getRunStderr() != null && !question.getRunStderr().isBlank()) {
                builder.append("错误输出:\n<<<\n")
                        .append(Texts.truncate(question.getRunStderr(), 2000))
                        .append("\n>>>\n");
            }
        }
        if (question.getCodeDraft() != null && !question.getCodeDraft().isBlank()) {
            builder.append("最终代码:\n<<<\n")
                    .append(Texts.truncate(question.getCodeDraft(), 6000))
                    .append("\n>>>\n");
        }
        if (question.getComment() != null && !question.getComment().isBlank()) {
            builder.append("面试官总评:").append(question.getComment()).append('\n');
        }
        builder.append('\n');
    }

    private InterviewDetail.ReportView toReportView(InterviewReport report, List<InterviewQuestion> questions,
                                                    JdProfile profile, List<WeaknessView> weaknesses) {
        InterviewDetail.CodingView coding = null;
        for (InterviewQuestion question : questions) {
            if (question.getQuestionType() != InterviewQuestionType.CODING) {
                continue;
            }
            coding = new InterviewDetail.CodingView(
                    question.getCodingTitle(),
                    question.getJudgeStatus(),
                    question.getRunExitCode(),
                    question.getRunStdout(),
                    question.getRunStderr(),
                    question.getRunRuntimeMs(),
                    question.getScore(),
                    question.getComment());
            break;
        }

        Integer jdMatchScore = null;
        List<InterviewDetail.JdCoverageView> coverageViews = new ArrayList<>();
        List<String> jdGaps = new ArrayList<>();
        if (profile != null && profile.competencies() != null && !profile.competencies().isEmpty()) {
            Map<String, Integer> selfScores = selfScores(questions);
            int weightSum = 0;
            int weighted = 0;
            for (JdProfile.Competency competency : profile.competencies()) {
                if (competency == null || competency.name() == null || competency.name().isBlank()) {
                    continue;
                }
                InterviewReport.Coverage llmCoverage = findCoverage(report.jdCoverage(), competency.name());
                Integer score = llmCoverage == null ? null : llmCoverage.score();
                if (score == null) {
                    score = selfScores.get(competency.name());
                }
                int weight = competency.weight() == null ? 0 : Math.max(0, competency.weight());
                coverageViews.add(new InterviewDetail.JdCoverageView(competency.name(), weight, score,
                        llmCoverage == null ? null : llmCoverage.evidence()));
                if (score != null && weight > 0) {
                    weightSum += weight;
                    weighted += weight * score;
                }
            }
            if (weightSum > 0) {
                jdMatchScore = Math.round((float) weighted / weightSum);
            }

            if (report.jdGaps() != null) {
                jdGaps.addAll(report.jdGaps());
            }
            if (jdGaps.isEmpty()) {
                for (InterviewQuestion question : questions) {
                    String gap = InterviewViews.jdGapOf(objectMapper, question.getEvaluationJson());
                    if (gap != null && !gap.isBlank()) {
                        jdGaps.add(gap);
                    }
                }
            }
            jdGaps = new ArrayList<>(new LinkedHashSet<>(jdGaps));
            if (jdGaps.size() > 3) {
                jdGaps = new ArrayList<>(jdGaps.subList(0, 3));
            }
        }

        return new InterviewDetail.ReportView(report.overallScore(), report.dimensionScores(),
                report.weakPoints(), report.highlights(), report.advice(),
                jdMatchScore, coverageViews, jdGaps, coding,
                weaknesses == null ? List.of() : weaknesses);
    }

    /** 每道题最后一条候选人回答，作为弱项证据的兜底 */
    private Map<Long, String> lastAnswers(List<InterviewQuestion> questions) {
        Map<Long, String> result = new HashMap<>();
        for (InterviewQuestion question : questions) {
            for (InterviewMessage message : threadOf(question.getId())) {
                if (ROLE_USER.equals(message.getRole()) && message.getContent() != null) {
                    result.put(question.getId(), message.getContent());
                }
            }
        }
        return result;
    }

    /** 每道题 1~5 分转成 0~100,按能力项求平均,作为模型没给覆盖分时的兜底 */
    private static Map<String, Integer> selfScores(List<InterviewQuestion> questions) {
        Map<String, int[]> accumulator = new HashMap<>();
        for (InterviewQuestion question : questions) {
            if (question.getCompetency() == null || question.getCompetency().isBlank()
                    || question.getScore() == null) {
                continue;
            }
            int[] value = accumulator.computeIfAbsent(question.getCompetency(), key -> new int[2]);
            value[0] += question.getScore();
            value[1]++;
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, int[]> entry : accumulator.entrySet()) {
            int[] value = entry.getValue();
            result.put(entry.getKey(), Math.round(value[0] * 20f / value[1]));
        }
        return result;
    }

    private static InterviewReport.Coverage findCoverage(List<InterviewReport.Coverage> coverage, String name) {
        if (coverage == null || coverage.isEmpty()) {
            return null;
        }
        for (InterviewReport.Coverage item : coverage) {
            if (item != null && item.name() != null && item.name().equalsIgnoreCase(name)) {
                return item;
            }
        }
        for (InterviewReport.Coverage item : coverage) {
            if (item != null && item.name() != null
                    && (item.name().contains(name) || name.contains(item.name()))) {
                return item;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "结果序列化失败");
        }
    }

    /** HR 面不做 JD 技术能力项覆盖统计 */
    private static boolean isHrRound(InterviewSession session) {
        return session.getRound() != null && session.getRound().contains("HR");
    }
}
