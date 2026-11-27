package com.grindset.backend.dto;

import java.util.List;

/**
 * 系统推荐的单场面试计划。前端只做展示和确认,实际出题仍按会话配置执行。
 */
public record InterviewPlan(
        String mode,
        String round,
        int durationMin,
        int questionCount,
        List<Item> mix,
        List<Competency> competencies,
        String style,
        List<String> rationale,
        List<String> warnings) {

    /** 面试结构:项目深挖 / 基础八股 / 系统设计 / 行为面 / 手撕代码 */
    public record Item(String type, String label, int count) {
    }

    /** 本场重点能力项,source 为 JD / 自选 / 系统推荐 */
    public record Competency(String name, int weight, String source) {
    }
}
