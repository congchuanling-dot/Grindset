package com.grindset.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.AnswerRequest;
import com.grindset.backend.dto.AnswerResult;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.CreateInterviewRequest;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewSummary;
import com.grindset.backend.llm.LlmException;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.interview.InterviewReportService;
import com.grindset.backend.service.interview.InterviewSessionService;
import com.grindset.backend.service.interview.InterviewTurnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.SseTimeouts.DEFAULT_MS;

/**
 * AI 面试接口。
 * POST   /api/interviews                        创建会话
 * GET    /api/interviews                        历史会话
 * GET    /api/interviews/{id}                   会话详情(刷新恢复)
 * POST   /api/interviews/{id}/questions         SSE 生成下一道主问题
 * POST   /api/interviews/{id}/answers           提交回答,返回点评 + 追问/评分
 * POST   /api/interviews/{id}/finish            生成复盘报告
 * DELETE /api/interviews/{id}                   删除会话
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private final InterviewSessionService sessionService;
    private final InterviewTurnService turnService;
    private final InterviewReportService reportService;
    private final ObjectMapper objectMapper;

    public InterviewController(InterviewSessionService sessionService,
                               InterviewTurnService turnService,
                               InterviewReportService reportService,
                               ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.turnService = turnService;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ApiResponse<InterviewDetail> create(@AuthenticationPrincipal LoginUser loginUser,
                                              @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(sessionService.create(userId(loginUser), request));
    }

    @GetMapping
    public ApiResponse<List<InterviewSummary>> list(@AuthenticationPrincipal LoginUser loginUser,
                                                    @RequestParam(required = false) List<Long> resumeIds) {
        return ApiResponse.ok(sessionService.list(userId(loginUser), resumeIds));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                               @PathVariable long id) {
        return ApiResponse.ok(sessionService.detail(userId(loginUser), id));
    }

    /** 出题走 SSE,和对话页保持一致的 delta / done / error 事件 */
    @PostMapping("/{id}/questions")
    public SseEmitter nextQuestion(@AuthenticationPrincipal LoginUser loginUser,
                                   @PathVariable long id) {
        long ownerId = userId(loginUser);
        SseEmitter emitter = new SseEmitter(DEFAULT_MS);
        Thread.ofVirtual().name("interview-sse-", 0).start(() -> runQuestion(ownerId, id, emitter));
        return emitter;
    }

    @PostMapping("/{id}/answers")
    public ApiResponse<AnswerResult> answer(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable long id,
                                            @RequestBody AnswerRequest request) {
        return ApiResponse.ok(turnService.answer(userId(loginUser), id, request));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<InterviewDetail.ReportView> finish(@AuthenticationPrincipal LoginUser loginUser,
                                                          @PathVariable long id) {
        return ApiResponse.ok(reportService.finish(userId(loginUser), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable long id) {
        sessionService.delete(userId(loginUser), id);
        return ApiResponse.ok(null);
    }

    private void runQuestion(long userId, long id, SseEmitter emitter) {
        try {
            InterviewDetail.QuestionView question = turnService.nextQuestion(userId, id,
                    text -> sendEvent(emitter, "delta", Map.of("text", text)));
            sendEvent(emitter, "done", Map.of(
                    "questionId", question.id(),
                    "seq", String.valueOf(question.seq())));
            emitter.complete();
        } catch (ClientGoneException e) {
            log.debug("客户端已断开出题流");
            emitter.complete();
        } catch (BizException e) {
            fail(emitter, e.getMessage());
        } catch (LlmException e) {
            fail(emitter, e.getMessage());
        } catch (Exception e) {
            log.error("AI 出题失败", e);
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
            throw new ClientGoneException();
        }
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }

    /** 客户端已断开,不是业务错误 */
    private static class ClientGoneException extends RuntimeException {
    }
}
