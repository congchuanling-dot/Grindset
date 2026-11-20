package com.grindset.backend.llm;

import java.util.List;

/**
 * 一次带工具的完整调用结果。
 * content 和 toolCalls 至少有一个非空:模型要么调工具,要么直接回答。
 */
public record LlmCompletion(String content, List<LlmToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
