package com.grindset.backend.dto;

/** 训练计划列表项。 */
public record TrainingPlanView(
        String id,
        String title,
        String sourceType,
        String stage,
        int status,
        int itemCount,
        int completedCount,
        String createdAt,
        String expiresAt) {
}
