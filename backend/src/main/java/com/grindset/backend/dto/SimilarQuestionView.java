package com.grindset.backend.dto;

/** 同类题推荐项。 */
public record SimilarQuestionView(
        String questionId,
        String title,
        String domain,
        Integer difficulty) {
}
