package com.grindset.backend.common;

/**
 * 五维能力模型。知识点通过题库 type/domain 映射到维度。
 */
public enum TrainingDimension {

    BASICS("基础"),
    PROJECT("项目"),
    SYSTEM_DESIGN("系统设计"),
    COMMUNICATION("表达"),
    CODING("编码");

    private final String label;

    TrainingDimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TrainingDimension from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BASICS;
        }
        String value = raw.strip();
        for (TrainingDimension dimension : values()) {
            if (dimension.name().equalsIgnoreCase(value) || dimension.label.equals(value)) {
                return dimension;
            }
        }
        return BASICS;
    }

    /** 题库题目 -> 维度 */
    public static TrainingDimension of(QuestionType type, JudgeMode mode, String domain) {
        if (mode == JudgeMode.CODING || type == QuestionType.CODE) {
            return CODING;
        }
        if (type == QuestionType.PROJECT) {
            return PROJECT;
        }
        if (type == QuestionType.SCENARIO) {
            return SYSTEM_DESIGN;
        }
        String value = domain == null ? "" : domain;
        if (value.contains("项目")) {
            return PROJECT;
        }
        if (value.contains("系统设计") || value.contains("架构") || value.contains("场景")) {
            return SYSTEM_DESIGN;
        }
        if (value.contains("表达") || value.contains("沟通")) {
            return COMMUNICATION;
        }
        return BASICS;
    }

    /** 复盘报告里的中文维度 -> 枚举 */
    public static TrainingDimension ofReportDimension(String name) {
        if (name == null) {
            return BASICS;
        }
        return switch (name.strip()) {
            case "项目" -> PROJECT;
            case "系统设计" -> SYSTEM_DESIGN;
            case "表达" -> COMMUNICATION;
            case "编码" -> CODING;
            default -> BASICS;
        };
    }
}
