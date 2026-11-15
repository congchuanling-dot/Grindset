package com.grindset.backend.controller;

import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.LoginRequest;
import com.grindset.backend.dto.RegisterRequest;
import com.grindset.backend.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口。只负责"接待":收请求、调 Service、包响应。业务逻辑都在 AuthService。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        Long userId = authService.register(req.getAccount(), req.getPassword());
        return ApiResponse.ok(Map.of("userId", userId));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest req) {
        Long userId = authService.login(req.getAccount(), req.getPassword());
        // 暂时先返回 userId;下一步换成签发 JWT + 种 refresh Cookie
        return ApiResponse.ok(Map.of("userId", userId));
    }
}
