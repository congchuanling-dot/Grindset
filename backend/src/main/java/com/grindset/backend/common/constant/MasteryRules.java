package com.grindset.backend.common.constant;

/**
 * 掌握度与错题相关的业务规则阈值。
 */
public final class MasteryRules {

    /** 本题评分小于等于该值视为未掌握,自动收录/更新错题本 */
    public static final int WRONG_SCORE_MAX = 2;

    /** 超过该天数未练习视为陈旧知识点,加权出题时优先抽中 */
    public static final int STALE_DAYS = 30;

    private MasteryRules() {
    }
}
