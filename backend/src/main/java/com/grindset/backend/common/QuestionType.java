package com.grindset.backend.common;

/**
 * 题库题型。LLM/导入数据出现未知值时统一降级为 BASICS。
 */
public enum QuestionType {

    BASICS,
    SCENARIO,
    PROJECT,
    CODE;

    public static QuestionType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BASICS;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BASICS;
        }
    }
}
