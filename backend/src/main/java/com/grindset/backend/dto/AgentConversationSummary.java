package com.grindset.backend.dto;

/**
 * 会话列表项。前端左侧边栏只需要这几个字段。
 */
public record AgentConversationSummary(
        String id,
        String title,
        int messageCount,
        String lastMessageAt,
        String createdAt) {
}
