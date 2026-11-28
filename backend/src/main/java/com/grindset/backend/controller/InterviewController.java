package com.grindset.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.AnswerRequest;
import com.grindset.backend.dto.AnswerResult;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.CodeRunRequest;
import com.grindset.backend.dto.CodingChatRequest;
import com.grindset.backend.dto.CreateInterviewRequest;
import com.grindset.backend.dto.InterviewCodingResultView;
import com.grindset.backend.dto.InterviewCodingView;
import com.grindset.backend.dto.InterviewDetail;
import com.grindset.backend.dto.InterviewPlanPreview;
import com.grindset.backend.dto.InterviewPlanRequest;
import com.grindset.backend.dto.InterviewSummary;
import com.grindset.backend.llm.LlmException;
import com.grindset.backend.security.CurrentUser;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.interview.InterviewCodingService;
import com.grindset.backend.service.interview.InterviewPlanService;
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

import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.SseTimeouts.STREAM_TIMEOUT_5_MIN_MS;

/**
 * AI 面试接口。
 * POST   /api/interviews                        创建会话
 * POST   /api/interviews/plan                   创建前预览岗位画像与推荐计划
 * GET    /api/interviews                        历史会话
 * GET    /api/interviews/{id}                   会话详情(刷新恢复)
 * POST   /api/interviews/{id}/questions         SSE 生成下一道主问题
 * POST   /api/interviews/{id}/answers           提交回答,返回点评 + 追问/评分
 * POST   /api/interviews/{id}/coding/start      开始最后一题手撕代码
 * POST   /api/interviews/{id}/coding/chat       编程阶段与面试官流式交流
 * POST   /api/interviews/{id}/coding/run        运行完整代码并返回控制台输出
 * POST   /api/interviews/{id}/coding/draft      保存代码草稿
 * POST   /api/interviews/{id}/coding/complete   完成本题
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
    private final InterviewCodingService codingService;
    private final InterviewPlanService planService;
    private final ObjectMapper objectMapper;

    public InterviewController(InterviewSessionService sessionService,
                               InterviewTurnService turnService,
                               InterviewReportService reportService,
                               InterviewCodingService codingService,
                               InterviewPlanService planService,
                               ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.turnService = turnService;
        this.reportService = reportService;
        this.codingService = codingService;
        this.planService = planService;
        this.objectMapper = objectMapper;
    }

    /** 创建前预览:解析 JD 并返回推荐计划,不落库 */
    @PostMapping("/plan")
    public ApiResponse<InterviewPlanPreview> plan(@AuthenticationPrincipal LoginUser loginUser,
                                                  @RequestBody InterviewPlanRequest request) {
        return ApiResponse.ok(planService.preview(CurrentUser.requireId(loginUser), request));
    }

    @PostMapping
    public ApiResponse<InterviewDetail> create(@AuthenticationPrincipal LoginUser loginUser,
                                              @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(sessionService.create(CurrentUser.requireId(loginUser), request));
    }

    @GetMapping
    public ApiResponse<List<InterviewSummary>> list(@AuthenticationPrincipal LoginUser loginUser,
                                                    @RequestParam(required = false) List<Long> resumeIds) {
        return ApiResponse.ok(sessionService.list(CurrentUser.requireId(loginUser), resumeIds));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                               @PathVariable long id) {
        return ApiResponse.ok(sessionService.detail(CurrentUser.requireId(loginUser), id));
    }

    /**
     * 出题走 SSE,和对话页保持一致的 delta / done / error 事件。
     * done 里额外带 questionId / seq,前端拿到就能直接渲染,不用再查一次详情。
     */
    @PostMapping("/{id}/questions")
    public SseEmitter nextQuestion(@AuthenticationPrincipal LoginUser loginUser,
                                   @PathVariable long id) {
        long ownerId = CurrentUser.requireId(loginUser);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_5_MIN_MS);
        // 出题可能跑几十秒,用虚拟线程挂住,不占 Tomcat 平台线程
        Thread.ofVirtual().name("interview-sse-", 0).start(() -> runQuestion(ownerId, id, emitter));
        return emitter;
    }

    @PostMapping("/{id}/answers")
    public ApiResponse<AnswerResult> answer(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable long id,
                                            @RequestBody AnswerRequest request) {
        return ApiResponse.ok(turnService.answer(CurrentUser.requireId(loginUser), id, request));
    }

    @PostMapping("/{id}/coding/start")
    public ApiResponse<InterviewCodingView> startCoding(@AuthenticationPrincipal LoginUser loginUser,
                                                        @PathVariable long id) {
        return ApiResponse.ok(codingService.start(CurrentUser.requireId(loginUser), id));
    }

    @PostMapping("/{id}/coding/run")
    public ApiResponse<InterviewCodingView.RunView> runCoding(@AuthenticationPrincipal LoginUser loginUser,
                                                              @PathVariable long id,
                                                              @RequestBody CodeRunRequest request) {
        return ApiResponse.ok(codingService.run(CurrentUser.requireId(loginUser), id, request));
    }

    /** 保存代码草稿;前端输入停止后防抖调用 */
    @PostMapping("/{id}/coding/draft")
    public ApiResponse<Void> saveCodingDraft(@AuthenticationPrincipal LoginUser loginUser,
                                             @PathVariable long id,
                                             @RequestBody CodeRunRequest request) {
        codingService.saveDraft(CurrentUser.requireId(loginUser), id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/coding/complete")
    public ApiResponse<InterviewCodingResultView> completeCoding(@AuthenticationPrincipal LoginUser loginUser,
                                                                 @PathVariable long id) {
        return ApiResponse.ok(codingService.complete(CurrentUser.requireId(loginUser), id));
    }

    /** 编程阶段与面试官交流:SSE 增量返回,前端边收边播报 */
    @PostMapping("/{id}/coding/chat")
    public SseEmitter codingChat(@AuthenticationPrincipal LoginUser loginUser,
                                 @PathVariable long id,
                                 @RequestBody CodingChatRequest request) {
        long ownerId = CurrentUser.requireId(loginUser);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_5_MIN_MS);
        Thread.ofVirtual().name("interview-coding-chat-", 0)
                .start(() -> runCodingChat(ownerId, id, request, emitter));
        return emitter;
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<InterviewDetail.ReportView> finish(@AuthenticationPrincipal LoginUser loginUser,
                                                          @PathVariable long id) {
        return ApiResponse.ok(reportService.finish(CurrentUser.requireId(loginUser), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable long id) {
        sessionService.delete(CurrentUser.requireId(loginUser), id);
        return ApiResponse.ok(null);
    }

    private void runQuestion(long userId, long id, SseEmitter emitter) {
        SseEvents events = new SseEvents(emitter, objectMapper);
        try {
            InterviewDetail.QuestionView question = turnService.nextQuestion(userId, id,
                    text -> events.send("delta", Map.of("text", text)));
            events.send("done", Map.of(
                    "questionId", question.id(),
                    "seq", String.valueOf(question.seq())));
            events.complete();
        } catch (SseEvents.ClientGoneException e) {
            log.debug("客户端已断开出题流");
            events.complete();
        } catch (BizException e) {
            events.fail(e.getMessage());
        } catch (LlmException e) {
            events.fail(e.getMessage());
        } catch (Exception e) {
            log.error("AI 出题失败", e);
            events.fail("服务器内部错误");
        }
    }

    private void runCodingChat(long userId, long id, CodingChatRequest request, SseEmitter emitter) {
        SseEvents events = new SseEvents(emitter, objectMapper);
        try {
            codingService.streamChat(userId, id, request,
                    text -> events.send("delta", Map.of("text", text)));
            events.send("done", Map.of());
            events.complete();
        } catch (SseEvents.ClientGoneException e) {
            log.debug("客户端已断开编程交流流");
            events.complete();
        } catch (BizException e) {
            events.fail(e.getMessage());
        } catch (LlmException e) {
            events.fail(e.getMessage());
        } catch (Exception e) {
            log.error("面试编程交流失败", e);
            events.fail("服务器内部错误");
        }
    }
}
