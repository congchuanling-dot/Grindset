package com.grindset.backend.dto;

/** 面试会话列表项。 */
public record InterviewSummary(
        String id,
        String resumeFileName,
        String position,
        String company,
        String style,
        String round,
        int questionCount,
        int completedQuestions,
        int status,
        Integer overallScore,
        String createdAt) {
}
