package com.grindset.backend.common;

/**
 * 代码提交场景:BANK 题库练习 / INTERVIEW 面试手撕代码。
 * 两个场景共用判题链路,但统计与进度回写必须隔离。
 */
public enum SubmissionScene {

    BANK,
    INTERVIEW;

    public static SubmissionScene from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BANK;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BANK;
        }
    }
}
