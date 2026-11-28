package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.TrainingAnswerRequest;
import com.grindset.backend.dto.TrainingAnswerResult;
import com.grindset.backend.dto.TrainingDashboard;
import com.grindset.backend.dto.TrainingMistakeView;
import com.grindset.backend.dto.TrainingPlanCreateRequest;
import com.grindset.backend.dto.TrainingPlanDetail;
import com.grindset.backend.dto.TrainingPlanView;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.training.TrainingPlanService;
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
 * 弱项训练接口（v2）。
 * GET  /api/training/dashboard                          训练仪表盘
 * POST /api/training/sessions                           创建基线/日常/复测/错题训练
 * GET  /api/training/sessions                           我的训练列表
 * GET  /api/training/sessions/{id}                      训练详情
 * POST /api/training/sessions/{id}/items/{itemId}/answer 提交作答
 * POST /api/training/sessions/{id}/abandon              放弃训练
 * GET  /api/training/mistakes                           错题本
 * POST /api/training/mistakes/retrain                   错题重练
 */
@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingPlanService trainingPlanService;

    public TrainingController(TrainingPlanService trainingPlanService) {
        this.trainingPlanService = trainingPlanService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<TrainingDashboard> dashboard(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(trainingPlanService.dashboard(userId(loginUser)));
    }

    @PostMapping("/sessions")
    public ApiResponse<TrainingPlanDetail> create(@AuthenticationPrincipal LoginUser loginUser,
                                                  @RequestBody(required = false) TrainingPlanCreateRequest request) {
        return ApiResponse.ok(trainingPlanService.create(userId(loginUser), request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<TrainingPlanView>> list(@AuthenticationPrincipal LoginUser loginUser,
                                                    @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(trainingPlanService.list(userId(loginUser), status));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<TrainingPlanDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                                  @PathVariable long id) {
        return ApiResponse.ok(trainingPlanService.detail(userId(loginUser), id));
    }

    @PostMapping("/sessions/{id}/items/{itemId}/answer")
    public ApiResponse<TrainingAnswerResult> answer(@AuthenticationPrincipal LoginUser loginUser,
                                                    @PathVariable long id,
                                                    @PathVariable long itemId,
                                                    @RequestBody TrainingAnswerRequest request) {
        String answer = request == null ? null : request.answer();
        return ApiResponse.ok(trainingPlanService.answer(userId(loginUser), id, itemId, answer));
    }

    @PostMapping("/sessions/{id}/abandon")
    public ApiResponse<Void> abandon(@AuthenticationPrincipal LoginUser loginUser,
                                     @PathVariable long id) {
        trainingPlanService.abandon(userId(loginUser), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/mistakes")
    public ApiResponse<List<TrainingMistakeView>> mistakes(@AuthenticationPrincipal LoginUser loginUser,
                                                           @RequestParam(required = false) Integer status,
                                                           @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(trainingPlanService.mistakes(userId(loginUser), status, limit));
    }

    @PostMapping("/mistakes/retrain")
    public ApiResponse<TrainingPlanDetail> retrainMistakes(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(trainingPlanService.retrainMistakes(userId(loginUser)));
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
