package com.grindset.backend.common.constant;

/**
 * 大模型 API Key 状态,对应 llm_keys.status 列。
 */
public final class LlmKeyStatus {

    /** 正常可用 */
    public static final int NORMAL = 0;

    /** 已失效(鉴权失败被自动停用) */
    public static final int INVALID = 1;

    /** 手动禁用 */
    public static final int DISABLED = 2;

    private LlmKeyStatus() {
    }
}
