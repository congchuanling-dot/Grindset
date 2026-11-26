package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.UsageDtos;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.AiUsageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token 用量查询,只返回当前登录用户自己的数据。
 * GET /api/usage/summary?days=7   汇总 + 业务/模型/按天分组
 * GET /api/usage/records?page=1   调用明细分页
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final AiUsageService usageService;

    public UsageController(AiUsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/summary")
    public ApiResponse<UsageDtos.Summary> summary(@AuthenticationPrincipal LoginUser loginUser,
                                                  @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(usageService.summary(userId(loginUser), days));
    }

    @GetMapping("/records")
    public ApiResponse<UsageDtos.RecordPage> records(@AuthenticationPrincipal LoginUser loginUser,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(usageService.records(userId(loginUser), page, size));
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return loginUser.userId();
    }
}
