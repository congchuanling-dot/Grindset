package com.grindset.backend.dto;

import java.util.List;

/**
 * 题库单次作答结果:AI 评分、命中/遗漏要点、点评建议,以及取回后的参考答案。
 */
public record QuestionAttemptResult(
        String attemptId,
        int score,
        List<String> hitPoints,
        List<String> missedPoints,
        String comment,
        String suggestion,
        String referenceAnswer,
        List<String> keyPoints,
        String status,
        int attemptCount,
        Integer bestScore,
        String createdAt) {
}
