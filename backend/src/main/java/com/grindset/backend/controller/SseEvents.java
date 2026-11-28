package com.grindset.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * 对话 / 面试等 SSE 接口共用的发送与收尾逻辑。
 *
 * <p>连接断开时抛 {@link ClientGoneException} 打断上游读取循环,
 * 由调用方按"客户端主动离开"静默收尾。</p>
 */
final class SseEvents {

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;

    SseEvents(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGoneException();
        }
    }

    void complete() {
        emitter.complete();
    }

    /** 发送 error 事件并结束;连接已断开时只做收尾 */
    void fail(String message) {
        try {
            send("error", Map.of("message", message));
        } catch (ClientGoneException ignored) {
            // 客户端已断开,无需再写
        }
        emitter.complete();
    }

    /** 客户端已断开,不是业务错误 */
    static final class ClientGoneException extends RuntimeException {
    }
}
