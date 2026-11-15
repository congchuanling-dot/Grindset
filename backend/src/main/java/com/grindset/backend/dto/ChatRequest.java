package com.grindset.backend.dto;

import java.util.List;

/**
 * 对话请求。messages 只放多轮历史(user/assistant),
 * system 提示词由服务端统一注入,前端不要传。
 */
public class ChatRequest {

    /** provider id,如 deepseek / qwen / zhipu / minimax / doubao */
    private String provider;

    /** 模型 ID */
    private String model;

    private List<Message> messages;

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

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    /** 单条消息 */
    public static class Message {

        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
