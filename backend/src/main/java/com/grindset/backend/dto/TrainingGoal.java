package com.grindset.backend.dto;

/** 训练计划生成时的目标快照，存进 training_plan.goal_json。 */
public record TrainingGoal(
        String kpKey,
        String kpName,
        String dimension,
        int beforeMastery,
        int severity) {
}
