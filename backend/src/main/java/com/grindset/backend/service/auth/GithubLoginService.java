package com.grindset.backend.service.auth;

import com.grindset.backend.config.OAuthFlowProperties;
import com.grindset.backend.dto.auth.AuthResult;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub 登录编排:只负责拼授权地址、调用 GitHub 接口,其余登录规则交给 SocialLoginService。
 * 客户端凭证从 oauth_client_config 表读取。
 */
@Service
public class GithubLoginService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String PROVIDER = "github";

    private final OAuthClientConfigService configService;
    private final OAuthFlowProperties flowProperties;
    private final GithubOAuthClient githubOAuthClient;
    private final SocialLoginService socialLoginService;

    public GithubLoginService(OAuthClientConfigService configService,
                              OAuthFlowProperties flowProperties,
                              GithubOAuthClient githubOAuthClient,
                              SocialLoginService socialLoginService) {
        this.configService = configService;
        this.flowProperties = flowProperties;
        this.githubOAuthClient = githubOAuthClient;
        this.socialLoginService = socialLoginService;
    }

    /** 生成 GitHub 授权地址,state 存 Redis 防 CSRF */
    public String authorizeUrl() {
        OAuthClientConfig config = configService.require(PROVIDER);
        String state = socialLoginService.createState(PROVIDER, flowProperties.getStateTtlMinutes());
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("scope", "read:user user:email")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    /** 处理 GitHub 回调:校验 state、换 token、绑定或建号、签发本系统令牌 */
    public SocialLoginService.CallbackResult handleCallback(OAuthClientConfig config,
                                                            String code, String state) {
        socialLoginService.verifyState(PROVIDER, state);
        String accessToken = githubOAuthClient.exchangeCode(code, config);
        GithubOAuthUser githubUser = githubOAuthClient.fetchUser(accessToken);
        SocialLoginService.SocialUser socialUser = new SocialLoginService.SocialUser(
                githubUser.githubId(),
                pickNickname(githubUser),
                githubUser.email(),
                // GitHub 只取已验证邮箱,拿到的邮箱可信
                true,
                githubUser.avatarUrl());
        return socialLoginService.completeLogin(PROVIDER, socialUser,
                flowProperties.getCodeTtlSeconds());
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
