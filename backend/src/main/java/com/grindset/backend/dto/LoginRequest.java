package com.grindset.backend.dto;

/**
 * 登录请求体。单独一个类,便于以后登录加字段(验证码/记住我)时不影响注册。
 */
public class LoginRequest {

    private String account;
    private String password;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
