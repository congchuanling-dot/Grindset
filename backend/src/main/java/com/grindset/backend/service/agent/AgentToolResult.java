package com.grindset.backend.service.agent;

/**
 * 一次工具执行的结果。
 * summary 给前端卡片做一行展示;data 序列化后回填给模型当观察结果。
 */
public record AgentToolResult(boolean ok, String summary, Object data) {
}
