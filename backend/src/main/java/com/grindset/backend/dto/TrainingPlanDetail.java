package com.grindset.backend.dto;

import java.util.List;

/** 训练会话详情：阶段、目标快照、题目与完成后的掌握度/维度变化。 */
public record TrainingPlanDetail(
        String id,
        String title,
        String sourceType,
        String stage,
        int status,
        int itemCount,
        int completedCount,
        int accuracy,
        Integer avgScore,
        String createdAt,
        String expiresAt,
        List<GoalView> goals,
        List<ItemView> items,
        List<MasteryDelta> result,
        List<DimensionDelta> dimensionResult) {

    /** 计划生成时的目标，beforeMastery 用于结果页对比。 */
    public record GoalView(String kpKey, String kpName, String dimension,
                           int beforeMastery, int severity) {
    }

    /** 一道训练题；题库题带 questionId，生成题 questionId 为空。 */
    public record ItemView(
            String id,
            int seq,
            String role,
            String kpKey,
            String kpName,
            String dimension,
            String dimensionLabel,
            String itemType,
            String source,
            String questionId,
            String title,
            String content,
            List<String> keyPoints,
            Integer difficulty,
            int status,
            Integer score,
            List<String> hitPoints,
            List<String> missedPoints,
            String comment,
            String suggestion,
            String referenceAnswer,
            String finishedAt,
            List<SimilarQuestionView> similarQuestions,
            String mistakeId) {
    }

    /** 某个知识点在一次训练后的变化。 */
    public record MasteryDelta(
            String kpKey,
            String kpName,
            String dimension,
            int before,
            int after,
            int delta,
            String level,
            String nextReviewAt,
            boolean eliminated) {
    }

    /** 某个能力维度在一次训练后的变化。 */
    public record DimensionDelta(
            String dimension,
            String label,
            int before,
            int after,
            int delta) {
    }
}
