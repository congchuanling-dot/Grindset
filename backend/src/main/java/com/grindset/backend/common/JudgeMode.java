package com.grindset.backend.common;

/**
 * 题目判题形态:TEXT 问答 / CODING 编程。
 */
public enum JudgeMode {

    TEXT,
    CODING;

    public static JudgeMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return TEXT;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TEXT;
        }
    }
}
