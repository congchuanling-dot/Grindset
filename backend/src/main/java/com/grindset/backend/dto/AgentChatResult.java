package com.grindset.backend.dto;

/**
 * 一轮 Agent 对话结束后的结果:会话 ID + 最新标题。
 * Controller 用它拼 SSE 的 done 事件,前端据此更新侧边栏。
 */
public record AgentChatResult(String conversationId, String title) {
}
