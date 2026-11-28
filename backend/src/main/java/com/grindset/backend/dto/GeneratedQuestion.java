package com.grindset.backend.dto;

/** 只包含 AI 生成题的必要字段，存进 training_plan_item.generated_json。 */
public record GeneratedQuestion(
        String kpName,
        String title,
        String content,
        java.util.List<String> keyPoints,
        String referenceAnswer,
        Integer difficulty,
        Integer suggestedMinutes) {
}
