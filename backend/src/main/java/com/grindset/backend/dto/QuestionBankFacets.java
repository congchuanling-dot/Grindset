package com.grindset.backend.dto;

import java.util.List;

/**
 * 题库筛选项:领域与题型及其题目数量,供前端动态生成筛选栏。
 * 只统计已上架题目;领域按数量倒序,题型按枚举顺序。
 */
public record QuestionBankFacets(
        List<Count> domains,
        List<Count> types,
        List<Count> tags,
        List<Count> companies) {

    public record Count(String value, int count) {
    }
}
