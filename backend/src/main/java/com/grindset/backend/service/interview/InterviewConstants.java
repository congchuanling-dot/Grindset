package com.grindset.backend.service.interview;

import java.util.Set;

/**
 * 面试域的状态码与消息角色/类型。拆分 service 后这些值跨类使用,集中在这里避免各写一份。
 */
public final class InterviewConstants {

    /** 消息角色:面试官 */
    public static final String ROLE_INTERVIEWER = "INTERVIEWER";

    /** 消息角色:候选人 */
    public static final String ROLE_USER = "USER";

    /** 消息类型:主问题 */
    public static final String TYPE_QUESTION = "QUESTION";

    /** 消息类型:追问 */
    public static final String TYPE_FOLLOW_UP = "FOLLOW_UP";

    /** 消息类型:候选人回答 */
    public static final String TYPE_ANSWER = "ANSWER";

    /** 消息类型:面试官点评 */
    public static final String TYPE_FEEDBACK = "FEEDBACK";

    /** 面试风格可选项:压力面 / 常规 / 轻松 */
    public static final Set<String> STYLES = Set.of("pressure", "middle", "relaxed");

    /** 单场面试可选的总题数 */
    public static final Set<Integer> QUESTION_COUNTS = Set.of(3, 5, 8);

    /** 单场面试最多选择的侧重点数量 */
    public static final int MAX_FOCUS = 4;

    /** 一道主问题最多追问次数,达到后强制收束 */
    public static final int MAX_FOLLOW_UP = 3;

    private InterviewConstants() {
    }
}
