package com.grindset.backend.llm;

/**
 * 对话中的一条消息。role 只有 user / assistant 两种,
 * system 提示词由服务端统一注入,不接受前端传入。
 */
public record ChatMessage(String role, String content) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
}
