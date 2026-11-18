package com.grindset.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第三方登录通用流程参数,对应 application.yml 的 app.oauth.*。
 * 客户端凭证不在这里,统一存放在 oauth_client_config 表(加密)。
 */
@Component
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthFlowProperties {

    /** state 有效期(分钟),防 CSRF,一次即废 */
    private long stateTtlMinutes = 5;

    /** 一次性 code 有效期(秒),给前端换 accessToken */
    private long codeTtlSeconds = 60;

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
