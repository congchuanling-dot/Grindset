package com.grindset.backend.dto;

/**
 * Agent 对话请求。
 *
 * <p>有 conversationId 时历史由后端从库里读,前端只传本次提问;
 * 没有 conversationId 时后端懒创建一个新会话。</p>
 */
public class AgentChatRequest {

    /** 会话 ID(字符串,避免前端 Long 精度丢失);新会话传 null 或空串 */
    private String conversationId;

    private String provider;

    private String model;

    /** 本次用户提问 */
    private String message;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
