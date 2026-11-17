package com.grindset.backend.service.agent;

import java.util.Map;

/**
 * Agent 可调用的只读工具。实现类只负责查询,不接收前端传入的 userId,
 * 统一由 AgentService 从 JWT 里取,避免越权。
 */
public interface AgentTool {

    /** 工具名,模型在 JSON 里用它指定要调用哪个 */
    String name();

    /** 展示给前端卡片的中文名 */
    String label();

    /** 写进系统提示词的说明,含参数约定 */
    String description();

    /**
     * 执行查询。
     *
     * @param userId 当前登录用户
     * @param args   模型给出的参数,可能为空
     */
    AgentToolResult execute(long userId, Map<String, Object> args);
}
