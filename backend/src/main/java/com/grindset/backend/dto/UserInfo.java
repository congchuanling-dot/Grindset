package com.grindset.backend.dto;

/** 当前登录用户的基本资料。 */
public class UserInfo {

    private final String userId;
    private final String nickname;
    private final String email;

    public UserInfo(String userId, String nickname, String email) {
        this.userId = userId;
        this.nickname = nickname;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }
}
