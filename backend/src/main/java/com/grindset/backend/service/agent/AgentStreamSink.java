package com.grindset.backend.service.agent;

import java.util.Map;

/**
 * Agent 运行过程中的事件出口。Controller 用 SSE 实现,Service 不直接依赖 SseEmitter。
 */
public interface AgentStreamSink {

    /** 阶段性提示,如"正在查询画像…" */
    void status(String text);

    /** 模型决定调用某个工具 */
    void toolCall(String id, String name, String label, Map<String, Object> args);

    /** 工具执行完成 */
    void toolResult(String id, String name, boolean ok, String summary);

    /** 最终回答的增量文本 */
    void delta(String text);
}
