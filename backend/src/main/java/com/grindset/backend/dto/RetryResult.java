package com.grindset.backend.dto;

/**
 * 重练结果:本次评分、点评、建议、参考要点,以及更新后的状态与下次复习时间。
 */
public record RetryResult(
        String wrongId,
        int score,
        String comment,
        String suggestion,
        String referenceAnswer,
        String status,
        int retryCount,
        Integer bestScore,
        String nextReviewAt) {
}
