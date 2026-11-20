package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * agent_messages 表:Agent 会话里的一条 LLM 消息。
 *
 * <p>存的是完整消息流,一条 assistant 可以只带 tool_calls,后面紧跟若干条 tool 结果,
 * 最后一轮再是一条普通的 assistant 回答。按 seq 升序就能原样重放给模型。</p>
 *
 * <p>tool_calls_json / tool_meta_json 用 JSON 列存字符串,读写都交给 Jackson,
 * 与 interview_sessions.report_json 的处理方式保持一致。</p>
 */
@TableName("agent_messages")
public class AgentMessage {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long conversationId;

    private Integer seq;

    /** USER / ASSISTANT / TOOL */
    private String role;

    private String content;

    private String toolCallsJson;

    private String toolCallId;

    private String toolMetaJson;

    /** 仅 assistant:0 正常 / 2 失败 / 3 中断 */
    private Integer status;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

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

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public void setToolCallsJson(String toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolMetaJson() {
        return toolMetaJson;
    }

    public void setToolMetaJson(String toolMetaJson) {
        this.toolMetaJson = toolMetaJson;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
