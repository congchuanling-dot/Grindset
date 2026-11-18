package com.grindset.backend.common.constant;

/**
 * 错题状态,对应 wrong_questions.status 列。
 */
public final class WrongQuestionStatus {

    /** 待重练 */
    public static final int PENDING = 0;

    /** 已掌握(重练得分达到阈值) */
    public static final int MASTERED = 1;

    private WrongQuestionStatus() {
    }
}
