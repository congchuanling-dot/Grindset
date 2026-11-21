package com.grindset.backend.common;

/**
 * 题库来源。PDF / CRAWL 为预留导入方式,当前只实际使用 MANUAL / AI。
 */
public enum QuestionSource {

    MANUAL,
    AI,
    PDF,
    CRAWL;

    public static QuestionSource from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MANUAL;
        }
    }
}
