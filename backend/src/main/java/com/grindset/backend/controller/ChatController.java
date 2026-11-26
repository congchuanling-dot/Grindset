package com.grindset.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.ChatRequest;
import com.grindset.backend.dto.ProviderInfo;
import com.grindset.backend.llm.LlmException;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.SseTimeouts.STREAM_TIMEOUT_5_MIN_MS;

/**
 * 对话接口。
 * GET  /api/chat/models  可用模型列表(只含有 key 的厂商)
 * POST /api/chat         SSE 流式对话,事件:delta(增量)/ done(结束)/ error(失败)
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/models")
    public ApiResponse<List<ProviderInfo>> models() {
        return ApiResponse.ok(chatService.listAvailableProviders());
    }

    @PostMapping
    public SseEmitter chat(@AuthenticationPrincipal LoginUser loginUser,
                           @RequestBody ChatRequest request) {
        // 用量记账需要用户维度;安全配置已要求登录,这里只做兜底
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        long userId = loginUser.userId();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_5_MIN_MS);
        // 虚拟线程:一个用户一条对话挂住不占平台线程,几十个人同时用也不怕
        Thread.ofVirtual().name("chat-sse-", 0).start(() -> runChat(userId, request, emitter));
        return emitter;
    }

    /** 在虚拟线程里跑完整条流。注意:参数错误也走 SSE error 事件(响应头已经发出去了) */
    private void runChat(long userId, ChatRequest request, SseEmitter emitter) {
        try {
            chatService.stream(request, userId, text -> sendEvent(emitter, "delta", Map.of("text", text)));
            sendEvent(emitter, "done", Map.of());
            emitter.complete();
        } catch (ClientGoneException e) {
            // 前端主动断开(点了停止 / 关了页面),上游已随 io 异常终止,这里静默收尾
            log.debug("客户端已断开对话流");
            emitter.complete();
        } catch (BizException e) {
            fail(emitter, e.getMessage());
        } catch (LlmException e) {
            fail(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("对话处理失败", e);
            fail(emitter, "服务器内部错误");
        }
    }

    private void fail(SseEmitter emitter, String message) {
        try {
            sendEvent(emitter, "error", Map.of("message", message));
            emitter.complete();
        } catch (ClientGoneException e) {
            emitter.complete();
        }
    }

    private void sendEvent(SseEmitter emitter, String event, Map<String, String> data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            // 连不上或连接已关闭:用运行时异常打断上游读取循环
            throw new ClientGoneException();
        }
    }

    /** 客户端已断开,不是业务错误,不打日志堆栈 */
    private static class ClientGoneException extends RuntimeException {
    }
}
