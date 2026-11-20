package com.grindset.backend.dto;

import java.util.List;

/**
 * 会话详情:刷新页面或点历史会话时,前端靠它完整恢复对话。
 */
public record AgentConversationDetail(
        String id,
        String title,
        int messageCount,
        String lastMessageAt,
        String createdAt,
        List<AgentMessageView> messages) {
}
