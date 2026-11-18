package com.grindset.backend.entity.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * oauth_client_config 表对应的实体。
 * client_secret 存的是 AES-GCM 密文,读取后需用 SecretCipher 解密。
 */
@TableName("oauth_client_config")
public class OAuthClientConfigEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** github / google */
    private String provider;

    private String clientId;

    /** AES-GCM 密文,以 v1: 开头 */
    private String clientSecret;

    private String redirectUri;

    private String frontendCallbackUri;

    /** 0 启用 / 1 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
