package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.config.JwtProperties;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.AuthResult;
import com.grindset.backend.dto.LoginRequest;
import com.grindset.backend.dto.RegisterRequest;
import com.grindset.backend.dto.UserInfo;
import com.grindset.backend.entity.User;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.security.TokenService;
import com.grindset.backend.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证接口。
 * 登录/注册签发 access token(响应体)+ refresh token(HttpOnly Cookie);
 * /refresh 轮换令牌,/logout 吊销 refresh,/me 返回当前用户资料。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    /** Cookie 只在认证接口上携带,减少暴露面 */
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, TokenService tokenService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResult> register(@RequestBody RegisterRequest req, HttpServletResponse response) {
        Long userId = authService.register(req.getAccount(), req.getPassword());
        return issue(userId, tokenService.issue(userId), response);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResult> login(@RequestBody LoginRequest req, HttpServletResponse response) {
        Long userId = authService.login(req.getAccount(), req.getPassword());
        return issue(userId, tokenService.issue(userId), response);
    }

    /** 用 Cookie 里的 refresh 换新的一对 token;旧 refresh 立即作废 */
    @PostMapping("/refresh")
    public ApiResponse<AuthResult> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        TokenService.Rotation rotation;
        try {
            rotation = tokenService.rotate(refreshToken);
        } catch (BizException e) {
            // refresh 已失效,顺手清掉浏览器里的死 Cookie
            clearRefreshCookie(response);
            throw e;
        }
        return issue(rotation.userId(), rotation.tokens(), response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenService.revoke(refreshToken);
        }
        clearRefreshCookie(response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        User user = authService.requireActiveUser(loginUser.userId());
        return ApiResponse.ok(new UserInfo(
                String.valueOf(user.getId()), user.getNickname(), user.getEmail()));
    }

    /** 统一把令牌写进响应体 + Cookie */
    private ApiResponse<AuthResult> issue(Long userId, TokenService.TokenPair tokens,
                                          HttpServletResponse response) {
        User user = authService.requireActiveUser(userId);
        writeRefreshCookie(response, tokens.refreshToken());
        return ApiResponse.ok(new AuthResult(String.valueOf(userId), tokens.accessToken(),
                tokens.accessExpiresIn(), user.getNickname()));
    }

    private void writeRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite(jwtProperties.getCookieSameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(jwtProperties.getRefreshTtlDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite(jwtProperties.getCookieSameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
