package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.CodeRunRequest;
import com.grindset.backend.dto.CodeRunResult;
import com.grindset.backend.dto.CodingDetail;
import com.grindset.backend.dto.CodingReviewResult;
import com.grindset.backend.dto.CodingReviewView;
import com.grindset.backend.dto.QuestionAnswerRequest;
import com.grindset.backend.dto.QuestionAttemptResult;
import com.grindset.backend.dto.QuestionBankDetail;
import com.grindset.backend.dto.QuestionBankFacets;
import com.grindset.backend.dto.QuestionBankView;
import com.grindset.backend.dto.QuestionNeighbors;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.CodingService;
import com.grindset.backend.service.QuestionBankService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题库接口。
 * GET  /api/questions                题目列表（领域/题型/难度/状态/关键词/分页）+ 全量统计
 * GET  /api/questions/stats          只取做题统计
 * GET  /api/questions/facets         领域与题型的动态筛选项（带数量）
 * GET  /api/questions/random         随机一题，可排除当前题
 * GET  /api/questions/{id}           题目详情 + 我的作答历史
 * GET  /api/questions/{id}/neighbors 默认排序下的前后相邻题
 * GET  /api/questions/{id}/attempts  某道题的作答历史
 * POST /api/questions/{id}/attempts  提交作答，AI 评分
 * GET  /api/questions/{id}/coding    编程题详情（题面 + main 骨架 + 最近点评）
 * POST /api/questions/{id}/run       自由运行完整代码，同步返回控制台输出
 * POST /api/questions/{id}/review    运行代码并让 AI 点评，回写做题进度
 * GET  /api/questions/{id}/reviews   我的点评历史
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionBankController {

    private final QuestionBankService questionBankService;
    private final CodingService codingService;

    public QuestionBankController(QuestionBankService questionBankService, CodingService codingService) {
        this.questionBankService = questionBankService;
        this.codingService = codingService;
    }

    @GetMapping
    public ApiResponse<QuestionBankView> list(@AuthenticationPrincipal LoginUser loginUser,
                                              @RequestParam(required = false) String domain,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) Integer difficulty,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String judgeMode,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(questionBankService.list(userId(loginUser), domain, type, difficulty,
                status, keyword, judgeMode, page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<QuestionBankView.Summary> stats(@AuthenticationPrincipal LoginUser loginUser,
                                                       @RequestParam(required = false) String judgeMode) {
        return ApiResponse.ok(questionBankService.stats(userId(loginUser), judgeMode));
    }

    @GetMapping("/facets")
    public ApiResponse<QuestionBankFacets> facets(@RequestParam(required = false) String judgeMode) {
        return ApiResponse.ok(questionBankService.facets(judgeMode));
    }

    @GetMapping("/random")
    public ApiResponse<String> random(@RequestParam(required = false) Long excludeId,
                                      @RequestParam(required = false) String judgeMode) {
        return ApiResponse.ok(questionBankService.randomQuestionId(excludeId, judgeMode));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionBankDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable long id) {
        return ApiResponse.ok(questionBankService.detail(userId(loginUser), id));
    }

    @GetMapping("/{id}/neighbors")
    public ApiResponse<QuestionNeighbors> neighbors(@PathVariable long id) {
        return ApiResponse.ok(questionBankService.neighbors(id));
    }

    @GetMapping("/{id}/attempts")
    public ApiResponse<List<QuestionBankDetail.AttemptView>> attempts(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
        return ApiResponse.ok(questionBankService.attempts(userId(loginUser), id));
    }

    @PostMapping("/{id}/attempts")
    public ApiResponse<QuestionAttemptResult> attempt(@AuthenticationPrincipal LoginUser loginUser,
                                                      @PathVariable long id,
                                                      @RequestBody QuestionAnswerRequest request) {
        String answer = request == null ? null : request.answer();
        return ApiResponse.ok(questionBankService.attempt(userId(loginUser), id, answer));
    }

    @GetMapping("/{id}/coding")
    public ApiResponse<CodingDetail> coding(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable long id) {
        return ApiResponse.ok(codingService.detail(userId(loginUser), id));
    }

    @PostMapping("/{id}/run")
    public ApiResponse<CodeRunResult> run(@AuthenticationPrincipal LoginUser loginUser,
                                          @PathVariable long id,
                                          @RequestBody CodeRunRequest request) {
        return ApiResponse.ok(codingService.run(userId(loginUser), id, request));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<CodingReviewResult> review(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable long id,
                                                  @RequestBody CodeRunRequest request) {
        return ApiResponse.ok(codingService.review(userId(loginUser), id, request));
    }

    @GetMapping("/{id}/reviews")
    public ApiResponse<List<CodingReviewView>> reviews(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
        return ApiResponse.ok(codingService.reviews(userId(loginUser), id));
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
