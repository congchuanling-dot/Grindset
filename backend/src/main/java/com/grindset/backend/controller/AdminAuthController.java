package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ClientIp;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.service.AdminAuthService;
import com.grindset.backend.service.auth.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台登录,只认配置里指定的账号(app.admin.account / password)。
 * 登录成功返回会话令牌,前端放 X-Admin-Token;普通用户 JWT 不能调这里。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final LoginRateLimiter loginRateLimiter;

    public AdminAuthController(AdminAuthService adminAuthService, LoginRateLimiter loginRateLimiter) {
        this.adminAuthService = adminAuthService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody LoginRequest req,
                                                  HttpServletRequest request) {
        String account = req == null || req.account() == null ? "" : req.account().strip();
        String password = req == null || req.password() == null ? "" : req.password();
        String clientIp = ClientIp.of(request);
        // 复用登录限流:5 次/分钟/账号、30 次/分钟/IP
        loginRateLimiter.check(account, clientIp);
        try {
            return ApiResponse.ok(Map.of("token", adminAuthService.login(account, password)));
        } catch (BizException e) {
            loginRateLimiter.recordFailure(account, clientIp);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(name = "X-Admin-Token", required = false) String token) {
        adminAuthService.logout(token);
        return ApiResponse.ok(null);
    }

    public record LoginRequest(String account, String password) {
    }
}
