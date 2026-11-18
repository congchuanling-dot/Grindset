package com.grindset.backend.common.constant;

/**
 * 题库题目上下架状态,对应 question_bank.status 列。
 */
public final class QuestionBankStatus {

    /** 已下架,刷题列表与作答接口都不可见 */
    public static final int OFFLINE = 0;

    /** 已上架 */
    public static final int ONLINE = 1;

    private QuestionBankStatus() {
    }
}
