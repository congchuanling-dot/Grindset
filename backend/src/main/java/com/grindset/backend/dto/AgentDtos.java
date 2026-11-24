package com.grindset.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 首页 Agent 的请求/响应对象。
 *
 * <p>字段都不大,统一收在一个容器里,避免一个 DTO 一个文件。嵌套类型在 JSON 里没有额外结构,
 * 不影响接口格式。</p>
 */
public final class AgentDtos {

    private AgentDtos() {
    }

    /** 对话请求:有 conversationId 时历史由后端从库里重放 */
    public static class ChatRequest {

        /** 会话 ID(字符串,避免前端 Long 精度丢失);新会话传 null 或空串 */
        private String conversationId;

        private String provider;

        private String model;

        /** 本次用户提问 */
        private String message;

        /** 给用户看的展示文案;为空时直接用 message。点击卡片选择时用它隐藏内部 ID */
        private String displayMessage;

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

        public String getDisplayMessage() {
            return displayMessage;
        }

        public void setDisplayMessage(String displayMessage) {
            this.displayMessage = displayMessage;
        }
    }

    /** 一轮对话结束后的结果:Controller 用它拼 SSE 的 done 事件 */
    public record ChatResult(String conversationId, String title) {
    }

    /** 会话列表项 */
    public record ConversationSummary(
            String id,
            String title,
            int messageCount,
            String lastMessageAt,
            String createdAt) {
    }

    /** 会话详情,消息已按一轮工具调用合并成展示结构 */
    public record ConversationDetail(
            String id,
            String title,
            int messageCount,
            String lastMessageAt,
            String createdAt,
            List<MessageView> messages) {
    }

    /** 一条展示消息:steps 放工具卡片,content 放最终回答 */
    public record MessageView(
            String id,
            String role,
            String content,
            int status,
            String createdAt,
            List<ToolStep> steps) {

        public static final String ROLE_USER = "user";
        public static final String ROLE_ASSISTANT = "assistant";
    }

    /** 一次工具调用的展示信息,字段与前端 AgentToolStep 对齐 */
    public record ToolStep(
            String id,
            String name,
            String label,
            Map<String, Object> args,
            boolean ok,
            String summary,
            Map<String, Object> data) {
    }

    /** 重命名会话请求 */
    public record RenameRequest(String title) {
    }
}
