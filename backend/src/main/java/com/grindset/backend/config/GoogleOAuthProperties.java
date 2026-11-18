package com.grindset.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google 登录(OAuth 2.0)配置,对应 application.yml 的 app.oauth.google.*。
 */
@Component
@ConfigurationProperties(prefix = "app.oauth.google")
public class GoogleOAuthProperties {

    /** Google Cloud Console 生成的 Client ID */
    private String clientId = "";

    /** Google Cloud Console 生成的 Client secret,禁止进仓库 */
    private String clientSecret = "";

    /** 回调地址,必须与 Console 里 Authorized redirect URI 完全一致 */
    private String redirectUri = "http://localhost:8081/api/auth/google/callback";

    /** 登录成功后跳回的前端页面,由它拿一次性 code 换令牌 */
    private String frontendCallbackUri = "http://localhost:5173/auth/callback";

    /** state 有效期(分钟) */
    private long stateTtlMinutes = 5;

    /** 一次性 code 有效期(秒) */
    private long codeTtlSeconds = 60;

    /** 未配置凭证时业务侧可据此快速失败 */
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getFrontendCallbackUri() {
        return frontendCallbackUri;
    }

    public void setFrontendCallbackUri(String frontendCallbackUri) {
        this.frontendCallbackUri = frontendCallbackUri;
    }

    public long getStateTtlMinutes() {
        return stateTtlMinutes;
    }

    public void setStateTtlMinutes(long stateTtlMinutes) {
        this.stateTtlMinutes = stateTtlMinutes;
    }

    public long getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public void setCodeTtlSeconds(long codeTtlSeconds) {
        this.codeTtlSeconds = codeTtlSeconds;
    }
}
