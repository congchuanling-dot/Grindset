package com.grindset.backend.common;

/**
 * 面试题目类型:VERBAL 语音问答 / CODING 手撕代码。
 */
public enum InterviewQuestionType {

    VERBAL,
    CODING;

    /** 历史数据没有该列或值非法时按语音题处理 */
    public static InterviewQuestionType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return VERBAL;
        }
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return VERBAL;
        }
    }
}
