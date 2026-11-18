package com.grindset.backend.controller.auth;

import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.auth.AuthResult;
import com.grindset.backend.dto.auth.GoogleExchangeRequest;
import com.grindset.backend.security.AuthCookieWriter;
import com.grindset.backend.service.auth.GoogleLoginService;
import com.grindset.backend.service.auth.OAuthClientConfig;
import com.grindset.backend.service.auth.OAuthClientConfigService;
import com.grindset.backend.service.auth.SocialLoginService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Google 登录接口。
 * authorize 跳转 Google 授权页;callback 处理授权结果并跳回前端;
 * exchange 让前端用一次性 code 换本系统令牌。凭证来自数据库。
 */
@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {

    private static final String PROVIDER = "google";

    private final GoogleLoginService googleLoginService;
    private final OAuthClientConfigService configService;
    private final AuthCookieWriter authCookieWriter;

    public GoogleAuthController(GoogleLoginService googleLoginService,
                                OAuthClientConfigService configService,
                                AuthCookieWriter authCookieWriter) {
        this.googleLoginService = googleLoginService;
        this.configService = configService;
        this.authCookieWriter = authCookieWriter;
    }

    /** 跳转 Google 授权页 */
    @GetMapping("/authorize")
    public void authorize(HttpServletResponse response) throws IOException {
        response.sendRedirect(googleLoginService.authorizeUrl());
    }

    /** Google 授权回调:校验 state、换令牌、写 refresh Cookie,再跳回前端 */
    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        OAuthClientConfig config = configService.require(PROVIDER);
        if (error != null || code == null || state == null) {
            redirectToFrontend(response, config, "error", "Google 授权已取消");
            return;
        }
        try {
            SocialLoginService.CallbackResult result =
                    googleLoginService.handleCallback(config, code, state);
            authCookieWriter.writeRefreshCookie(response, result.refreshToken());
            redirectToFrontend(response, config, "code", result.oneTimeCode());
        } catch (BizException e) {
            // 回调是浏览器跳转,失败也要回到前端页面而不是甩一段 JSON
            redirectToFrontend(response, config, "error", e.getMessage());
        }
    }

    /** 前端用一次性 code 换 access token */
    @PostMapping("/exchange")
    public ApiResponse<AuthResult> exchange(@RequestBody GoogleExchangeRequest req) {
        return ApiResponse.ok(googleLoginService.consume(req.getCode()));
    }

    /** 统一拼前端回调地址,带上 provider 让前端选择对应的 exchange 接口 */
    private void redirectToFrontend(HttpServletResponse response, OAuthClientConfig config,
                                    String key, String value) throws IOException {
        String url = UriComponentsBuilder.fromUriString(config.frontendCallbackUri())
                .queryParam("provider", PROVIDER)
                .queryParam(key, value)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(url);
    }
}
