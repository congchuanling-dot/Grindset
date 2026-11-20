package com.grindset.backend.service.agent;

/**
 * Agent 循环的实现参数。只服务于 {@link AgentService},故放在同包而不是 common.
 */
public final class AgentConstants {

    /** 最多几轮「决策 → 调工具」,防止模型反复绕圈消耗额度 */
    public static final int MAX_ROUNDS = 8;

    /** 单轮最多执行几个工具调用,超出的回错误消息给模型 */
    public static final int MAX_TOOL_CALLS_PER_ROUND = 4;

    /** 一整轮对话最多执行多少个工具调用 */
    public static final int MAX_TOTAL_TOOL_CALLS = 12;

    /** 整轮对话的耗时预算,超时后带着已有结果直接生成回答 */
    public static final long MAX_RUN_MILLIS = 180_000L;

    /** 消息总字符数上限,超过后从最老的完整 turn 开始整组丢弃 */
    public static final int MAX_CONTEXT_CHARS = 24_000;

    private AgentConstants() {
    }

    // TODO 上下文压缩后续换成 LLM 摘要,当前只做整组滑窗丢弃
}
