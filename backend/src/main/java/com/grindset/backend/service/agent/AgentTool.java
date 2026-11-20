package com.grindset.backend.service.agent;

import java.util.Map;

/**
 * Agent 可调用的只读工具。实现类只负责查询,不接收前端传入的 userId,
 * 统一由 AgentService 从 JWT 里取,避免越权。
 */
public interface AgentTool {

    /** 工具名,模型通过 function calling 指定要调用哪个 */
    String name();

    /** 展示给前端卡片的中文名 */
    String label();

    /** 给模型看的说明:做什么、返回什么字段 */
    String description();

    /** 入参的 JSON Schema;无参数的工具用默认值即可 */
    default Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    /**
     * 执行查询。
     *
     * @param userId 当前登录用户
     * @param args   模型给出的参数,可能为空
     */
    AgentToolResult execute(long userId, Map<String, Object> args);
}
