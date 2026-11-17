package com.grindset.backend.common.constant;

/**
 * 用户输入的长度与大小上限。
 *
 * <p>同一类输入在多个入口出现:面试回答、题库作答、错题重练都受同一个回答长度约束,
 * 聊天和 Agent 共用同一套消息限制。集中定义避免某个入口单独改动后前后端提示不一致。</p>
 */
public final class InputLimits {

    /** 演讲/评分反馈等落库错误信息的截断长度(判题错误输出也用它) */
    public static final int MAX_FEEDBACK_LENGTH = 4000;

    /** 回答类文本(面试作答、题库作答、错题重练)的最大字符数 */
    public static final int MAX_ANSWER_LENGTH = 4000;

    /** 提交判题的源代码最大字符数,同时用于提交记录落库 */
    public static final int MAX_CODE_LENGTH = 64 * 1024;

    /** 上传简历文件的字节上限:10MB */
    public static final int MAX_RESUME_FILE_BYTES = 10 * 1024 * 1024;

    /** 单条聊天消息的最大字符数(首页对话与 Agent 共用) */
    public static final int CHAT_MAX_MESSAGE_LENGTH = 8000;

    /** 一次请求最多携带的历史消息条数(首页对话与 Agent 共用) */
    public static final int CHAT_MAX_REQUEST_MESSAGES = 200;

    private InputLimits() {
    }
}
