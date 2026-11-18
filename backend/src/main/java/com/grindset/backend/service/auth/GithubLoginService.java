package com.grindset.backend.service.auth;

import com.grindset.backend.common.BizException;
import com.grindset.backend.config.GithubOAuthProperties;
import com.grindset.backend.dto.auth.AuthResult;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub 登录编排:只负责拼授权地址、调用 GitHub 接口,其余登录规则交给 SocialLoginService。
 */
@Service
public class GithubLoginService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String PROVIDER = "github";

    private final GithubOAuthProperties properties;
    private final GithubOAuthClient githubOAuthClient;
    private final SocialLoginService socialLoginService;

    public GithubLoginService(GithubOAuthProperties properties,
                              GithubOAuthClient githubOAuthClient,
                              SocialLoginService socialLoginService) {
        this.properties = properties;
        this.githubOAuthClient = githubOAuthClient;
        this.socialLoginService = socialLoginService;
    }

    /** 生成 GitHub 授权地址,state 存 Redis 防 CSRF */
    public String authorizeUrl() {
        if (!properties.isConfigured()) {
            throw new BizException(500, "GitHub 登录未配置");
        }
        String state = socialLoginService.createState(PROVIDER, properties.getStateTtlMinutes());
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", "read:user user:email")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    /** 处理 GitHub 回调:校验 state、换 token、绑定或建号、签发本系统令牌 */
    public SocialLoginService.CallbackResult handleCallback(String code, String state) {
        socialLoginService.verifyState(PROVIDER, state);
        String accessToken = githubOAuthClient.exchangeCode(code);
        GithubOAuthUser githubUser = githubOAuthClient.fetchUser(accessToken);
        SocialLoginService.SocialUser socialUser = new SocialLoginService.SocialUser(
                githubUser.githubId(),
                pickNickname(githubUser),
                githubUser.email(),
                // GitHub 只取已验证邮箱,拿到的邮箱可信
                true,
                githubUser.avatarUrl());
        return socialLoginService.completeLogin(PROVIDER, socialUser, properties.getCodeTtlSeconds());
    }

    /** 前端用一次性 code 换 access token */
    public AuthResult consume(String oneTimeCode) {
        return socialLoginService.consume(PROVIDER, oneTimeCode);
    }

    private static String pickNickname(GithubOAuthUser githubUser) {
        if (githubUser.nickname() != null && !githubUser.nickname().isBlank()) {
            return githubUser.nickname();
        }
        return githubUser.login();
    }
}
