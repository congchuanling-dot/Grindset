package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 面试会话详情:配置 + 所有题目线程 + 复盘报告。
 * 刷新页面后前端靠这个接口完整恢复。
 */
public record InterviewDetail(
        String id,
        String resumeId,
        String resumeFileName,
        String position,
        String company,
        String jobLevel,
        boolean jdEnabled,
        JdProfile jdProfile,
        String style,
        String round,
        List<String> focus,
        int questionCount,
        boolean codingEnabled,
        int codingTimeLimitSec,
        int completedQuestions,
        int status,
        Integer overallScore,
        String createdAt,
        List<QuestionView> questions,
        ReportView report) {

    public record QuestionView(
            String id,
            int seq,
            String type,
            int status,
            int followUpCount,
            Integer score,
            List<String> knowledgePoints,
            String competency,
            String jdGap,
            String comment,
            String suggestion,
            String codingQuestionId,
            String codingTitle,
            String judgeStatus,
            Integer passed,
            Integer total,
            String codingSubmissionId,
            String deadlineAt,
            List<MessageView> messages) {
    }

    public record MessageView(
            String id,
            int seq,
            String role,
            String type,
            String content,
            String createdAt) {
    }

    public record ReportView(
            int overallScore,
            Map<String, Integer> dimensionScores,
            List<String> weakPoints,
            List<String> highlights,
            List<String> advice,
            Integer jdMatchScore,
            List<JdCoverageView> jdCoverage,
            List<String> jdGaps,
            CodingView coding,
            List<WeaknessView> weaknesses) {
    }

    /** JD 各能力项的复盘结果;score 为空表示本场未考察到 */
    public record JdCoverageView(
            String name,
            Integer weight,
            Integer score,
            String evidence) {
    }

    /** 复盘里的手撕代码结果,由数据库真实运行结果填充,不依赖模型输出 */
    public record CodingView(
            String title,
            String status,
            Integer exitCode,
            String stdout,
            String stderr,
            Integer runtimeMs,
            Integer score,
            String comment) {
    }
}
