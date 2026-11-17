package com.grindset.backend.service.interview;

/**
 * 面试域的状态码与消息角色/类型。拆分 service 后这些值跨类使用,集中在这里避免各写一份。
 */
public final class InterviewConstants {

    public static final int SESSION_ONGOING = 0;
    public static final int SESSION_FINISHED = 1;

    public static final int QUESTION_ASKING = 1;
    public static final int QUESTION_ANSWERED = 2;
    public static final int QUESTION_DONE = 3;
    public static final int QUESTION_ABANDONED = 4;

    public static final String ROLE_INTERVIEWER = "INTERVIEWER";
    public static final String ROLE_USER = "USER";

    public static final String TYPE_QUESTION = "QUESTION";
    public static final String TYPE_FOLLOW_UP = "FOLLOW_UP";
    public static final String TYPE_ANSWER = "ANSWER";
    public static final String TYPE_FEEDBACK = "FEEDBACK";

    private InterviewConstants() {
    }
}
