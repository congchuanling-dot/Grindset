package com.grindset.backend.common.constant;

/**
 * SSE 长连接超时。首页对话、AI 面试出题、Agent 对话都用同一个值,
 * 改这里三处一起生效。
 */
public final class SseTimeouts {

    /** SSE 连接最长保持 5 分钟,超时由容器关闭 */
    public static final long DEFAULT_MS = 5 * 60 * 1000L;

    private SseTimeouts() {
    }
}
