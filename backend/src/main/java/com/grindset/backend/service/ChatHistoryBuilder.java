package com.grindset.backend.service;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.ChatRequest;
import com.grindset.backend.llm.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static com.grindset.backend.common.constant.InputLimits.CHAT_MAX_MESSAGE_LENGTH;
import static com.grindset.backend.common.constant.InputLimits.CHAT_MAX_REQUEST_MESSAGES;

/**
 * 首页对话和 Agent 共用的历史消息校验与裁剪。
 * systemPrompt 为空表示不注入;requireLastUser 为 true 时要求最后一条是用户提问。
 */
public final class ChatHistoryBuilder {

    private ChatHistoryBuilder() {
    }

    public static List<ChatMessage> build(List<ChatRequest.Message> raw, int maxHistory,
                                          String systemPrompt, boolean requireLastUser) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息不能为空");
        }
        if (raw.size() > CHAT_MAX_REQUEST_MESSAGES) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息数量过多");
        }

        List<ChatMessage> result = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt));
        }

        int from = Math.max(0, raw.size() - Math.max(1, maxHistory));
        for (int i = from; i < raw.size(); i++) {
            ChatRequest.Message item = raw.get(i);
            String role = Texts.strip(item.getRole());
            if (!ChatMessage.ROLE_USER.equals(role) && !ChatMessage.ROLE_ASSISTANT.equals(role)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "消息角色不合法");
            }
            String content = item.getContent() == null ? "" : item.getContent();
            if (content.isBlank()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
            }
            if (content.length() > CHAT_MAX_MESSAGE_LENGTH) {
                throw new BizException(ErrorCode.BAD_REQUEST, "单条消息不能超过 " + CHAT_MAX_MESSAGE_LENGTH + " 字");
            }
            result.add(new ChatMessage(role, content));
        }

        if (requireLastUser
                && !ChatMessage.ROLE_USER.equals(result.get(result.size() - 1).role())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "最后一条消息必须是提问");
        }
        return result;
    }
}
