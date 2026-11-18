package com.grindset.backend.service.auth;

import com.grindset.backend.common.BizException;
import com.grindset.backend.config.GoogleOAuthProperties;
import com.grindset.backend.dto.auth.AuthResult;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google 登录编排:只负责拼授权地址、调用 Google 接口,登录规则交给 SocialLoginService。
 */
@Service
public class GoogleLoginService {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String PROVIDER = "google";

    private final GoogleOAuthProperties properties;
    private final GoogleOAuthClient googleOAuthClient;
    private final SocialLoginService socialLoginService;

    public GoogleLoginService(GoogleOAuthProperties properties,
                              GoogleOAuthClient googleOAuthClient,
                              SocialLoginService socialLoginService) {
        this.properties = properties;
        this.googleOAuthClient = googleOAuthClient;
        this.socialLoginService = socialLoginService;
    }

    /** 生成 Google 授权地址,state 存 Redis 防 CSRF */
    public String authorizeUrl() {
        if (!properties.isConfigured()) {
            throw new BizException(500, "Google 登录未配置");
        }
        String state = socialLoginService.createState(PROVIDER, properties.getStateTtlMinutes());
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    /** 处理 Google 回调:校验 state、换 token、绑定或建号、签发本系统令牌 */
    public SocialLoginService.CallbackResult handleCallback(String code, String state) {
        socialLoginService.verifyState(PROVIDER, state);
        String accessToken = googleOAuthClient.exchangeCode(code);
        GoogleOAuthUser googleUser = googleOAuthClient.fetchUser(accessToken);
        SocialLoginService.SocialUser socialUser = new SocialLoginService.SocialUser(
                googleUser.sub(),
                googleUser.nickname(),
                googleUser.email(),
                googleUser.emailVerified(),
                googleUser.avatarUrl());
        return socialLoginService.completeLogin(PROVIDER, socialUser, properties.getCodeTtlSeconds());
    }

    /** 前端用一次性 code 换 access token */
    public AuthResult consume(String oneTimeCode) {
        return socialLoginService.consume(PROVIDER, oneTimeCode);
    }
}
