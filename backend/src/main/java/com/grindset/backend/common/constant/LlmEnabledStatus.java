package com.grindset.backend.common.constant;

/**
 * 大模型渠道/模型的启用标志,对应 llm_channels.enabled 与 llm_models.enabled 列。
 */
public final class LlmEnabledStatus {

    /** 停用 / 下架 */
    public static final int DISABLED = 0;

    /** 启用 / 上架 */
    public static final int ENABLED = 1;

    private LlmEnabledStatus() {
    }
}
