package com.grindset.backend.dto;

/** 创建训练会话请求。stage 为 BASELINE / DAILY / RETEST / MISTAKE。 */
public record TrainingPlanCreateRequest(String stage, String sourceId) {
}
