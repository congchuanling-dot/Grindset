package com.grindset.backend.dto;

/**
 * 注册请求体。account=账号名,password=明文密码(到 Service 里才加密)。
 */
public class RegisterRequest {

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
