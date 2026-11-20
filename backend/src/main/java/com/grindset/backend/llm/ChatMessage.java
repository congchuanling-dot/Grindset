package com.grindset.backend.llm;

import java.util.List;

/**
 * 对话中的一条消息。
 * 普通文本消息只有 role/content;助手发起工具调用时带 toolCalls,
 * 工具执行结果用 ROLE_TOOL + toolCallId。
 *
 * <p>toolCalls 和 toolCallId 互斥:前者只在 assistant 消息上,后者只在 tool 消息上。
 * 合成一个 record 是为了让"调用"和"结果"在同一条消息链里按 id 配对,序列化时不容易漏。</p>
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
