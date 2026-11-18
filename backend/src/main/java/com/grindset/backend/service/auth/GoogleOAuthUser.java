package com.grindset.backend.service.auth;

/**
 * Google 返回的用户信息快照。
 * sub 是 Google 账号的稳定唯一标识,邮箱可变,不能当身份主键。
 */
public record GoogleOAuthUser(String sub, String nickname, String email,
                              boolean emailVerified, String avatarUrl) {
}
