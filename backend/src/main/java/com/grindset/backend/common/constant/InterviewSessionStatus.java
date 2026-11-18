package com.grindset.backend.common.constant;

/**
 * 面试会话状态,对应 interview_sessions.status 列。
 */
public final class InterviewSessionStatus {

    /** 进行中,可以继续出题/答题 */
    public static final int ONGOING = 0;

    /** 已结束(已生成复盘或手动结束) */
    public static final int FINISHED = 1;

    private InterviewSessionStatus() {
    }
}
