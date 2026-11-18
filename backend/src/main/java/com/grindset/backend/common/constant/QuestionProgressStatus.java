package com.grindset.backend.common.constant;

/**
 * 用户做题进度,对应 user_question_progress.status 列。
 * 没有记录或作答次数为 0 时表示 TODO,不单独存状态值。
 */
public final class QuestionProgressStatus {

    /** 已做对(得分高于错题阈值) */
    public static final int DONE = 1;

    /** 做错(得分低于等于错题阈值) */
    public static final int WRONG = 2;

    private QuestionProgressStatus() {
    }
}
