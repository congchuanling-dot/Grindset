package com.grindset.backend.dto;

/**
 * 模型重练评分结果。字段可空,由 InterviewLlmService 归一后校验。
 */
public record RetryEvaluation(
        Integer score,
        String comment,
        String suggestion,
        String referenceAnswer) {
}
