package com.grindset.backend.dto;

/**
 * 登录/注册/刷新成功后的令牌响应。
 * userId 按字符串下发,避免雪花 ID 在 JS 端精度丢失。
 */
public class AuthResult {

    private final String userId;
    private final String accessToken;
    /** access token 有效期(秒) */
    private final long expiresIn;
    private final String nickname;

    public AuthResult(String userId, String accessToken, long expiresIn, String nickname) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.nickname = nickname;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getNickname() {
        return nickname;
    }
}
