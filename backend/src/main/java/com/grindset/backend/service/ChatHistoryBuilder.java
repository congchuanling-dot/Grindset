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

    /**
     * 校验并裁剪历史消息。
     *
     * <p>首页对话允许任意结尾(用户可能只发了半段历史);Agent 必须保证最后一条是用户提问,
     * 否则模型会接着助手的话继续往下说,所以需要 requireLastUser 这个开关。</p>
     *
     * <p>systemPrompt 传 null 表示不注入:Agent 用自己的决策提示词,首页对话用 app.llm.system-prompt。</p>
     */
    public static List<ChatMessage> build(List<ChatRequest.Message> raw, int maxHistory,
                                          String systemPrompt, boolean requireLastUser) {
        // 先卡请求规模,避免超大请求直接转发给模型
        if (raw == null || raw.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息不能为空");
        }
        if (raw.size() > CHAT_MAX_REQUEST_MESSAGES) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息数量过多");
        }

        List<ChatMessage> result = new ArrayList<>();
        // system 只放一条,且必须在最前面
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt));
        }

        // 只保留最近 maxHistory 条,更早的历史直接丢
        int from = Math.max(0, raw.size() - Math.max(1, maxHistory));
        for (int i = from; i < raw.size(); i++) {
            ChatRequest.Message item = raw.get(i);
            String role = Texts.strip(item.getRole());
            // 逐条校验角色和内容,任何一条不合法整单拒绝,不把脏数据发给模型
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

        // Agent 要求最后一条是提问,否则模型会接着助手的话往下说
        if (requireLastUser
                && !ChatMessage.ROLE_USER.equals(result.get(result.size() - 1).role())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "最后一条消息必须是提问");
        }
        return result;
    }
}
