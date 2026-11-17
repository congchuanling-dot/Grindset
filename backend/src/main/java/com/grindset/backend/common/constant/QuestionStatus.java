package com.grindset.backend.common.constant;

/**
 * 面试题的线程状态,对应 interview_questions.status 列。
 *
 * <p>面试答题、错题收录、画像重建三处都会读这个状态,数值必须一致。</p>
 */
public final class QuestionStatus {

    /** 已出题,尚未收到候选人回答(追问时会再次进入该状态) */
    public static final int ASKING = 1;

    /** 已作答,等待收束或继续追问 */
    public static final int ANSWERED = 2;

    /** 本题已收束并完成评分 */
    public static final int DONE = 3;

    /** 会话结束时仍未答完,标记为放弃 */
    public static final int ABANDONED = 4;

    private QuestionStatus() {
    }
}
