package com.grindset.backend.service.agent;

/**
 * Agent 循环的实现参数。只服务于 {@link AgentService},故放在同包而不是 common.
 */
public final class AgentConstants {

    /** 消息总字符数上限,超过后从最老的完整 turn 开始整组丢弃 */
    public static final int MAX_CONTEXT_CHARS = 24_000;

    private AgentConstants() {
    }

    // TODO 上下文压缩后续换成 LLM 摘要,当前只做整组滑窗丢弃
}
