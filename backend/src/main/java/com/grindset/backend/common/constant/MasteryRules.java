package com.grindset.backend.common.constant;

/**
 * 掌握度与错题相关的业务规则阈值。
 *
 * <p>这些值同时被面试评分、题库作答、错题重练、画像计算使用,集中在一处避免改漏:
 * 例如错题阈值改了,题库和面试两条链路必须同步。</p>
 */
public final class MasteryRules {

    /** 本题评分小于等于该值视为未掌握,自动收录/更新错题本 */
    public static final int WRONG_SCORE_MAX = 2;

    /** 重练评分大于等于该值视为已掌握,错题状态从待复习转为已掌握 */
    public static final int MASTERED_SCORE_MIN = 3;

    /** 掌握度低于该值标记为 WEAK(薄弱) */
    public static final int WEAK_THRESHOLD = 40;

    /** 掌握度达到该值标记为 MASTERED(已掌握),介于两者之间为 LEARNING */
    public static final int MASTERED_THRESHOLD = 75;

    /** 超过该天数未练习视为陈旧知识点,加权出题时优先抽中 */
    public static final int STALE_DAYS = 30;

    /** EMA 中历史掌握度的权重,越大越稳定;本次观测权重为 1 - 该值 */
    public static final double EMA_OLD_WEIGHT = 0.7;

    /** 重练得分低于掌握线时的复习间隔(天) */
    public static final int REVIEW_DAYS_LOW = 1;

    /** 重练得分刚好达到掌握线时的复习间隔(天) */
    public static final int REVIEW_DAYS_MID = 3;

    /** 重练得分高于掌握线时的复习间隔(天) */
    public static final int REVIEW_DAYS_HIGH = 7;

    private MasteryRules() {
    }
}
