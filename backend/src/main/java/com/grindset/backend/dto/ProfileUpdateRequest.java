package com.grindset.backend.dto;

/** 修改个人资料请求。目前只支持昵称。 */
public class ProfileUpdateRequest {

    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
