package com.grindset.backend.llm;

/**
 * 模型返回的一次工具调用。
 *
 * <p>arguments 保持厂商返回的原始 JSON 字符串,不在这里解析:
 * 参数可能是脏数据,交给 AgentService 决定是重试还是把错误回填给模型,
 * 解析失败不应该让整轮对话直接崩掉。</p>
 */
public record LlmToolCall(String id, String name, String arguments) {
}
