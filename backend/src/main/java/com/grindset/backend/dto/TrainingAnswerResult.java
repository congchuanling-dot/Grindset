package com.grindset.backend.dto;

import java.util.List;

/** 训练题提交结果：评分、掌握度/维度变化、下一题难度、同类题与错题。 */
public record TrainingAnswerResult(
        String itemId,
        int score,
        List<String> hitPoints,
        List<String> missedPoints,
        String comment,
        String suggestion,
        boolean alreadyCompleted,
        TrainingPlanDetail.MasteryDelta mastery,
        TrainingPlanDetail.DimensionDelta dimension,
        boolean planCompleted,
        int completedCount,
        int itemCount,
        int nextDifficulty,
        List<SimilarQuestionView> similarQuestions,
        String mistakeId,
        boolean eliminated,
        String summary,
        String detailedFeedback,
        List<String> improvements,
        String referenceAnswer,
        String questionType,
        List<TrainingEvaluation.ScoringPoint> scoringPoints) {
}
