package com.grindset.backend.dto.auth;

/** Google 登录回调后,前端用一次性 code 换令牌的请求体。 */
public class GoogleExchangeRequest {

    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
