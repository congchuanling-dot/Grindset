package com.grindset.backend.service.training;

/** 某个目标知识点需要补几道题、用什么难度、扮演什么角色。 */
public record TrainingTargetSlot(TrainingTarget target, int count, int difficulty, String role) {
}
