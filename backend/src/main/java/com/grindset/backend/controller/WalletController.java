package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.WalletDtos;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.WalletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户钱包,只返回当前登录用户自己的数据。
 * GET /api/wallet             余额概览
 * GET /api/wallet/logs?page=1 余额流水分页
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ApiResponse<WalletDtos.Info> info(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(walletService.getInfo(userId(loginUser)));
    }

    @GetMapping("/logs")
    public ApiResponse<WalletDtos.LogPage> logs(@AuthenticationPrincipal LoginUser loginUser,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(walletService.logs(userId(loginUser), page, size));
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return loginUser.userId();
    }
}
