package com.grindset.backend.service.agent;

import com.grindset.backend.llm.ChatMessage;

import java.util.List;

/**
 * 一轮 Agent 交互产出的消息流(不含 system,可直接落库)。
 *
 * <p>aborted=true 表示客户端中途断开:消息流可能只到工具调用、最终回答也可能是半截,
 * 调用方仍应原样保存,刷新后至少能看到已经生成的部分。</p>
 */
public record AgentTurn(List<ChatMessage> messages, boolean aborted) {
}
