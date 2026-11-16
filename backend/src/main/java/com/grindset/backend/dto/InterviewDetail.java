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
        String style,
        String round,
        List<String> focus,
        int questionCount,
        int completedQuestions,
        int status,
        Integer overallScore,
        String createdAt,
        List<QuestionView> questions,
        ReportView report) {

    public record QuestionView(
            String id,
            int seq,
            int status,
            int followUpCount,
            Integer score,
            List<String> knowledgePoints,
            String comment,
            String suggestion,
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
            List<String> advice) {
    }
}
