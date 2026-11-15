package com.grindset.backend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理接口的简易令牌校验。令牌来自环境变量 ADMIN_TOKEN,
 * 请求头 X-Admin-Token 必须一致;没配令牌时管理接口直接不可用,避免裸奔。
 */
@Component
public class AdminGuard {

    private final String token;

    public AdminGuard(@Value("${app.admin.token:}") String token) {
        this.token = token == null ? "" : token.strip();
    }

    public void check(String provided) {
        if (token.isEmpty()) {
            throw new BizException(403, "未配置管理令牌,管理接口已停用");
        }
        if (provided == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(401, "管理令牌无效");
        }
    }
}
