package com.grindset.backend.dto;

/**
 * FREE_RUN 手撕代码完成后的结果:最近一次运行摘要 + 本题评分 + 面试官点评。
 */
public record InterviewCodingResultView(
        String status,
        int exitCode,
        String stdout,
        String stderr,
        int runtimeMs,
        int score,
        String comment,
        String suggestion,
        String nextAction,
        int completedQuestions,
        int questionCount) {
}
