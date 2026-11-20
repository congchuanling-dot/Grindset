package com.grindset.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.ChatRequest;
import com.grindset.backend.llm.LlmException;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.agent.AgentService;
import com.grindset.backend.service.agent.AgentStreamSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.grindset.backend.common.constant.SseTimeouts.STREAM_TIMEOUT_5_MIN_MS;

/**
 * Agent 对话接口。
 * POST /api/agent/chat  SSE 事件:status / tool_call / tool_result / delta / done / error。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public AgentController(AgentService agentService, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public SseEmitter chat(@AuthenticationPrincipal LoginUser loginUser, @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_5_MIN_MS);
        long userId = loginUser.userId();
        // SSE 会挂住整个请求周期,用虚拟线程,别占满 Tomcat 的工作线程
        Thread.ofVirtual().name("agent-sse-", 0).start(() -> runChat(userId, request, emitter));
        return emitter;
    }

    private void runChat(long userId, ChatRequest request, SseEmitter emitter) {
        try {
            agentService.stream(userId, request, new SseSink(emitter));
            send(emitter, "done", Map.of());
            emitter.complete();
        } catch (ClientGoneException e) {
            // 前端点了停止或关掉页面,静默收尾
            log.debug("客户端已断开 Agent 流");
            emitter.complete();
        } catch (BizException e) {
            fail(emitter, e.getMessage());
        } catch (LlmException e) {
            fail(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("Agent 处理失败", e);
            fail(emitter, "服务器内部错误");
        }
    }

    private void fail(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", Map.of("message", message));
            emitter.complete();
        } catch (ClientGoneException e) {
            emitter.complete();
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            // 响应头已经发出,连接断了也改不了状态码,只能当作客户端已离开
            throw new ClientGoneException();
        }
    }

    /** 把 Service 的事件回调翻译成 SSE 帧 */
    private class SseSink implements AgentStreamSink {

        private final SseEmitter emitter;

        private SseSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void status(String text) {
            send(emitter, "status", Map.of("text", text));
        }

        @Override
        public void toolCall(String id, String name, String label, Map<String, Object> args) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("label", label);
            data.put("args", args == null ? Map.of() : args);
            send(emitter, "tool_call", data);
        }

        @Override
        public void toolResult(String id, String name, boolean ok, String summary) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("ok", ok);
            data.put("summary", summary);
            send(emitter, "tool_result", data);
        }

        @Override
        public void delta(String text) {
            send(emitter, "delta", Map.of("text", text));
        }
    }

    /** 客户端已断开,不是业务错误 */
    private static class ClientGoneException extends RuntimeException {
    }
}
