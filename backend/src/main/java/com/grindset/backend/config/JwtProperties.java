package com.grindset.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 与 refresh Cookie 的配置。
 * 对应 application.yml 的 app.jwt.*,密钥生产环境必须走环境变量。
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥,至少 32 字节 */
    private String secret = "";

    /** access token 有效期(分钟) */
    private long accessTtlMinutes = 30;

    /** refresh token 有效期(天) */
    private long refreshTtlDays = 7;

    /** refresh Cookie 是否只在 https 下发;本地 http 开发必须为 false */
    private boolean cookieSecure = false;

    /** refresh Cookie 的 SameSite:Lax(同站)/ None(跨站,需配合 cookieSecure=true) */
    private String cookieSameSite = "Lax";

    /** 秒数形式,方便签发响应里的 expiresIn */
    public long accessTtlSeconds() {
        return accessTtlMinutes * 60;
    }

    public long refreshTtlSeconds() {
        return refreshTtlDays * 24 * 60 * 60;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTtlMinutes() {
        return accessTtlMinutes;
    }

    public void setAccessTtlMinutes(long accessTtlMinutes) {
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public long getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(long refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }
}
