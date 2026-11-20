package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 会话详情里的一条展示消息。
 *
 * <p>库里存的是原始消息流(assistant tool_calls + tool 结果 + 最终回答),
 * 这里已经把一轮工具调用合并成一个助手气泡:steps 放工具卡片,content 放最终回答。</p>
 */
public record AgentMessageView(
        String id,
        String role,
        String content,
        int status,
        String createdAt,
        List<AgentToolStepView> steps) {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** 一次工具调用的展示信息,字段与前端 AgentToolStep 对齐 */
    public record AgentToolStepView(
            String id,
            String name,
            String label,
            Map<String, Object> args,
            boolean ok,
            String summary) {
    }
}
