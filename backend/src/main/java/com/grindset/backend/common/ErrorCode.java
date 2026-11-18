package com.grindset.backend.common;

/**
 * 业务错误码。
 * <p>
 * 本项目的 {@link BizException#getCode()} 直接复用 HTTP 状态码语义,
 * GlobalExceptionHandler 用 {@code HttpStatus.resolve(code)} 映射响应状态,
 * 因此这里只维护常用状态码常量。业务代码一律引用常量,不要写裸数字。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    /** 400 参数不合法、缺少必填项 */
    public static final int BAD_REQUEST = 400;
    /** 401 未登录、令牌失效、账号或密码错误 */
    public static final int UNAUTHORIZED = 401;
    /** 403 无权限、账号被禁用 */
    public static final int FORBIDDEN = 403;
    /** 404 资源不存在(归属校验失败也返回它,避免泄露资源是否存在) */
    public static final int NOT_FOUND = 404;
    /** 409 冲突:重复注册、状态不允许、正在处理中 */
    public static final int CONFLICT = 409;
    /** 413 请求体或文件超过大小限制 */
    public static final int PAYLOAD_TOO_LARGE = 413;
    /** 422 内容无法处理(文件损坏、扫描件等) */
    public static final int UNPROCESSABLE_ENTITY = 422;
    /** 429 请求过于频繁、并发任务受限 */
    public static final int TOO_MANY_REQUESTS = 429;
    /** 500 服务端内部错误 */
    public static final int INTERNAL_ERROR = 500;
    /** 502 上游(LLM 等)返回异常 */
    public static final int BAD_GATEWAY = 502;
    /** 503 依赖服务不可用 */
    public static final int SERVICE_UNAVAILABLE = 503;
}
