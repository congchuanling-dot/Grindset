package com.grindset.backend.llm;

/**
 * 调用模型服务失败。message 已经是可读的中文文案,直接透给前端展示。
 * upstreamStatus 保留厂商原始状态码,用于判断是不是 key 失效(401/403)。
 */
public class LlmException extends RuntimeException {

    private final int code;

    /** 厂商返回的 HTTP 状态码,非上游失败时为 null */
    private final Integer upstreamStatus;

    public LlmException(int code, String message) {
        this(code, message, null);
    }

    public LlmException(int code, String message, Integer upstreamStatus) {
        super(message);
        this.code = code;
        this.upstreamStatus = upstreamStatus;
    }

    public int getCode() {
        return code;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }
}
