package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.RetryRequest;
import com.grindset.backend.dto.RetryResult;
import com.grindset.backend.dto.WrongQuestionDetail;
import com.grindset.backend.dto.WrongQuestionView;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.WrongQuestionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题本接口。
 * GET  /api/wrong-questions          错题列表 + 全量 summary
 * GET  /api/wrong-questions/{id}     错题详情 + 重练历史
 * POST /api/wrong-questions/{id}/retry 提交重练
 */
@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    @GetMapping
    public ApiResponse<WrongQuestionView> list(@AuthenticationPrincipal LoginUser loginUser,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long kpId,
                                               @RequestParam(required = false) String kp,
                                               @RequestParam(required = false) Long sessionId,
                                               @RequestParam(defaultValue = "50") int limit) {
        long userId = userId(loginUser);
        return ApiResponse.ok(wrongQuestionService.list(userId, status, kpId, kp, sessionId, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<WrongQuestionDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                                   @PathVariable long id) {
        return ApiResponse.ok(wrongQuestionService.detail(userId(loginUser), id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<RetryResult> retry(@AuthenticationPrincipal LoginUser loginUser,
                                          @PathVariable long id,
                                          @RequestBody RetryRequest request) {
        String answer = request == null ? null : request.getAnswer();
        return ApiResponse.ok(wrongQuestionService.retry(userId(loginUser), id, answer));
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
