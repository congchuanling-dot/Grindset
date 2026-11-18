package com.grindset.backend.service.auth;

/**
 * GitHub 返回的用户信息快照。
 * githubId 用数字 id(而非可改名的 login)作为身份标识。
 */
public record GithubOAuthUser(String githubId, String login, String nickname,
                              String email, String avatarUrl) {
}
