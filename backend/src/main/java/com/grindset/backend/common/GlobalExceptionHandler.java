package com.grindset.backend.common;

import com.grindset.backend.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * 全局异常处理:把抛出的异常统一转成 ApiResponse,前端永远收到规整的 {code,message,data}。
 * HTTP 状态码与业务 code 保持一致(400/401/409/500),前端既可按 HTTP status 也可按 code 判断。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常:用它自带的 code 和 message,HTTP 状态码同步映射 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        HttpStatus status = HttpStatus.resolve(e.getCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /** 请求体不是合法 JSON:属于客户端问题,返回 400 而不是 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.BAD_REQUEST, "请求体格式错误"));
    }

    /** 路径不存在:返回 404,不要被兜底逻辑误报成 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ErrorCode.NOT_FOUND, "接口不存在"));
    }

    /** 上传超过限制:Spring 在进入 Controller 之前就会抛,统一转成 413 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCode.PAYLOAD_TOO_LARGE, "文件不能超过 10MB"));
    }

    /** Redis 不可用:登录/刷新依赖它,给用户明确提示而不是 500 */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedis(RedisConnectionFailureException e) {
        log.error("Redis 连接失败", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ErrorCode.SERVICE_UNAVAILABLE, "会话服务暂不可用，请稍后再试"));
    }

    /** 客户端中途断开(关页面/停止生成):响应通常已提交,不能再写 JSON,安静收尾即可 */
    @ExceptionHandler(IOException.class)
    public void handleClientAbort(IOException e) {
        log.debug("客户端连接已断开:{}", e.getMessage());
    }

    /** 兜底:未预料的异常,日志留全量堆栈便于排查,响应不暴露细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("未处理的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }
}
