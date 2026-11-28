package com.grindset.backend.service.training;

import com.grindset.backend.common.TrainingStage;

import java.util.Set;

/** 生成训练题的上下文；与具体题目来源解耦，题库/AI provider 共用。 */
public record TrainingGenerationContext(
        long userId,
        TrainingStage stage,
        String sourceType,
        Long sourceId,
        String position,
        String company,
        String resumeText,
        Set<Long> excludedQuestionIds) {
}
