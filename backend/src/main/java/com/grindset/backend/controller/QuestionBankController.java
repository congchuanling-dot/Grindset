package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.QuestionAnswerRequest;
import com.grindset.backend.dto.QuestionAttemptResult;
import com.grindset.backend.dto.QuestionBankDetail;
import com.grindset.backend.dto.QuestionBankView;
import com.grindset.backend.security.LoginUser;
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
 * GET  /api/questions                题目列表（领域/难度/状态/关键词/分页）+ 全量统计
 * GET  /api/questions/stats          只取做题统计
 * GET  /api/questions/{id}           题目详情 + 我的作答历史
 * GET  /api/questions/{id}/attempts  某道题的作答历史
 * POST /api/questions/{id}/attempts  提交作答，AI 评分
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    public QuestionBankController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @GetMapping
    public ApiResponse<QuestionBankView> list(@AuthenticationPrincipal LoginUser loginUser,
                                              @RequestParam(required = false) String domain,
                                              @RequestParam(required = false) Integer difficulty,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(questionBankService.list(userId(loginUser), domain, difficulty,
                status, keyword, page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<QuestionBankView.Summary> stats(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(questionBankService.stats(userId(loginUser)));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionBankDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable long id) {
        return ApiResponse.ok(questionBankService.detail(userId(loginUser), id));
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

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
