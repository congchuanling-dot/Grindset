package com.grindset.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.AgentDtos;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.llm.LlmException;
import com.grindset.backend.security.CurrentUser;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.agent.AgentConversationService;
import com.grindset.backend.service.agent.AgentService;
import com.grindset.backend.service.agent.AgentStreamSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.SseTimeouts.STREAM_TIMEOUT_5_MIN_MS;

/**
 * 首页 Agent 接口。
 * POST   /api/agent/chat                         SSE 流式对话
 * GET    /api/agent/conversations                会话列表
 * GET    /api/agent/conversations/{id}           会话详情(刷新/点历史时恢复)
 * PATCH  /api/agent/conversations/{id}           重命名
 * DELETE /api/agent/conversations/{id}           删除
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentConversationService conversationService;
    private final ObjectMapper objectMapper;

    public AgentController(AgentConversationService conversationService, ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 对话
    // ------------------------------------------------------------------

    @PostMapping("/chat")
    public SseEmitter chat(@AuthenticationPrincipal LoginUser loginUser,
                           @RequestBody AgentDtos.ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_5_MIN_MS);
        long userId = CurrentUser.requireId(loginUser);
        // SSE 会挂住整个请求周期,用虚拟线程,别占满 Tomcat 的工作线程
        Thread.ofVirtual().name("agent-sse-", 0).start(() -> runChat(userId, request, emitter));
        return emitter;
    }

    private void runChat(long userId, AgentDtos.ChatRequest request, SseEmitter emitter) {
        SseSink sink = new SseSink(emitter);
        try {
            AgentDtos.ChatResult result = conversationService.chat(userId, request, sink);
            // done 带会话 ID 和最新标题,前端据此更新侧边栏并结束"生成中"状态
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conversationId", result.conversationId());
            data.put("title", result.title() == null ? "" : result.title());
            send(emitter, "done", data);
            emitter.complete();
        } catch (AgentService.StreamAbortedException e) {
            // 前端点了停止或关掉页面,内容已经由 Service 落库,静默收尾
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
        } catch (AgentService.StreamAbortedException e) {
            emitter.complete();
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            // 响应头已经发出,连接断了也改不了状态码,只能当作客户端已离开
            throw new AgentService.StreamAbortedException();
        }
    }

    // ------------------------------------------------------------------
    // 会话管理
    // ------------------------------------------------------------------

    @GetMapping("/conversations")
    public ApiResponse<List<AgentDtos.ConversationSummary>> list(
            @AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(conversationService.list(CurrentUser.requireId(loginUser)));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<AgentDtos.ConversationDetail> detail(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
        return ApiResponse.ok(conversationService.detail(CurrentUser.requireId(loginUser), id));
    }

    @PatchMapping("/conversations/{id}")
    public ApiResponse<Void> rename(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable long id,
                                    @RequestBody AgentDtos.RenameRequest request) {
        conversationService.rename(CurrentUser.requireId(loginUser), id, request == null ? null : request.title());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable long id) {
        conversationService.delete(CurrentUser.requireId(loginUser), id);
        return ApiResponse.ok(null);
    }

    /** 把 Service 的事件回调翻译成 SSE 帧 */
    private class SseSink implements AgentStreamSink {

        private final SseEmitter emitter;

        private SseSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void conversation(String conversationId, String title) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conversationId", conversationId);
            data.put("title", title);
            send(emitter, "conversation", data);
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
        public void toolResult(String id, String name, boolean ok, String summary, Object result) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("name", name);
            data.put("ok", ok);
            data.put("summary", summary);
            data.put("data", result == null ? Map.of() : result);
            send(emitter, "tool_result", data);
        }

        @Override
        public void delta(String text) {
            send(emitter, "delta", Map.of("text", text));
        }
    }
}
