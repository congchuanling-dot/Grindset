package com.grindset.backend.common;

/**
 * 业务异常。Service 层发现问题(如账号已存在、密码错误)直接抛,
 * 由 GlobalExceptionHandler 统一转成错误响应,不用层层 return 错误码。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
