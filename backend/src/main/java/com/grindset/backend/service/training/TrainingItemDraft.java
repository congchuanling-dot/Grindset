package com.grindset.backend.service.training;

/**
 * 生成器内部的一道题草稿。题库题只带 questionId；AI 生成题带 generatedJson。
 * 新增题目来源（例如代码题）时只实现新的 provider，不改会话编排。
 */
public record TrainingItemDraft(
        String kpKey,
        String kpName,
        String dimension,
        String role,
        String itemType,
        Integer difficulty,
        Long questionId,
        String generatedJson,
        String title) {
}
