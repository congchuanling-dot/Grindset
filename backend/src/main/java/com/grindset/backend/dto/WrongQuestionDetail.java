package com.grindset.backend.dto;

import java.util.List;

/**
 * 错题详情:首次重练前 originalAnswer 为 null、retries 为空,
 * 避免用户直接照抄原回答和参考要点。
 */
public record WrongQuestionDetail(
        String id,
        String questionContent,
        String questionContext,
        List<String> knowledgePoints,
        Integer originalScore,
        String originalComment,
        String originalSuggestion,
        String originalFeedback,
        String originalAnswer,
        String status,
        int retryCount,
        Integer bestScore,
        Integer lastScore,
        String nextReviewAt,
        String createdAt,
        String sessionId,
        String position,
        String round,
        List<RetryView> retries) {

    public record RetryView(
            String id,
            String answer,
            int score,
            String comment,
            String suggestion,
            String referenceAnswer,
            String createdAt) {
    }
}
