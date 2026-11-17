package com.grindset.backend.dto;

/**
 * 模型对一道题库题的评分结果。字段可空,由 InterviewLlmService 归一后校验。
 */
public record BankEvaluation(
        Integer score,
        java.util.List<String> hitPoints,
        java.util.List<String> missedPoints,
        String comment,
        String suggestion) {
}
