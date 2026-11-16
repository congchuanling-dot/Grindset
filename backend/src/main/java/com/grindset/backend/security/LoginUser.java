package com.grindset.backend.security;

/**
 * 放进 SecurityContext 的当前登录用户。
 * 只带 userId,需要昵称/邮箱等资料时再查库。
 */
public record LoginUser(Long userId) {
}
