package com.grindset.backend.llm;

/**
 * 模型返回的一次工具调用。arguments 是原始 JSON 字符串,由调用方解析。
 */
public record LlmToolCall(String id, String name, String arguments) {
}
