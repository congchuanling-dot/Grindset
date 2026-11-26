package com.grindset.backend.common;

import com.grindset.backend.service.AdminAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理接口校验。两种令牌都认:
 * 1. 环境变量 ADMIN_TOKEN 的静态令牌,给运维直接调接口用;
 * 2. 管理后台登录后签发的 Redis 会话令牌,给前端管理页用。
 * 普通用户的 JWT 在这里不生效。
 */
@Component
public class AdminGuard {

    private final String token;
    private final AdminAuthService adminAuthService;

    public AdminGuard(@Value("${app.admin.token:}") String token, AdminAuthService adminAuthService) {
        this.token = token == null ? "" : token.strip();
        this.adminAuthService = adminAuthService;
    }

    public void check(String provided) {
        if (!token.isEmpty() && provided != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            return;
        }
        if (adminAuthService.isValid(provided)) {
            return;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录管理后台");
    }
}
