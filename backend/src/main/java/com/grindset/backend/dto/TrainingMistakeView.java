package com.grindset.backend.dto;

import java.util.List;

/** 错题本条目。 */
public record TrainingMistakeView(
        String id,
        String kpKey,
        String kpName,
        String dimension,
        String dimensionLabel,
        int score,
        int status,
        int reviewCount,
        String nextReviewAt,
        String createdAt,
        String title,
        String questionId,
        String question,
        List<String> missedPoints,
        String comment,
        String suggestion) {
}
