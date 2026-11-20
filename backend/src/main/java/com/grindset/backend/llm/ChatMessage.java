package com.grindset.backend.llm;

import java.util.List;

/**
 * 对话中的一条消息。
 * 普通文本消息只有 role/content;助手发起工具调用时带 toolCalls,
 * 工具执行结果用 ROLE_TOOL + toolCallId。
 */
public record ChatMessage(String role, String content,
                          List<LlmToolCall> toolCalls, String toolCallId) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_TOOL = "tool";

    /** 普通文本消息,兼容原有调用 */
    public ChatMessage(String role, String content) {
        this(role, content, List.of(), null);
    }

    /** 助手发起工具调用,正文为空 */
    public static ChatMessage assistantToolCalls(List<LlmToolCall> calls) {
        return new ChatMessage(ROLE_ASSISTANT, "", calls, null);
    }

    /** 工具执行结果,必须带对应调用的 id */
    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage(ROLE_TOOL, content, List.of(), toolCallId);
    }
}
