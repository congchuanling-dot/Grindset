package com.grindset.backend.llm;

import java.util.List;

/**
 * 一次带工具的完整调用结果。
 * content 和 toolCalls 至少有一个非空:模型要么调工具,要么直接回答。
 * usage 用于 token 记账,上游没返回时是估算值或 {@link LlmUsage#EMPTY}。
 */
public record LlmCompletion(String content, List<LlmToolCall> toolCalls, LlmUsage usage) {

    /** 这一轮是否要执行工具;false 表示模型已经给出最终回答 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
