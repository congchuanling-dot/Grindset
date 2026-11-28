package com.grindset.backend.common.constant;

/**
 * 弱项训练的闭集值。数据库存字符串/数字，业务代码统一引用常量。
 */
public final class TrainingConstants {

    public static final String ROLE_PRACTICE = "PRACTICE";
    public static final String ROLE_RETEST = "RETEST";

    public static final int PLAN_PENDING = 0;
    public static final int PLAN_RUNNING = 1;
    public static final int PLAN_COMPLETED = 2;
    public static final int PLAN_EXPIRED = 3;

    public static final int ITEM_TODO = 0;
    public static final int ITEM_DONE = 1;

    /** 训练作答最少字数，与题库保持一致 */
    public static final int MIN_ANSWER_LENGTH = 10;

    private TrainingConstants() {
    }
}
