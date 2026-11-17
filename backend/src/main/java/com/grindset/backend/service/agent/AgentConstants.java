package com.grindset.backend.service.agent;

/**
 * Agent 循环的实现参数。只服务于 {@link AgentService},故放在同包而不是 common.
 */
public final class AgentConstants {

    /** 最多几轮「决策 → 调工具」,防止模型反复绕圈消耗额度 */
    public static final int MAX_STEPS = 5;

    /** 单个工具结果回填给模型前的截断长度,防止一次查询把上下文撑爆 */
    public static final int MAX_OBSERVATION_CHARS = 4000;

    private AgentConstants() {
    }
}
