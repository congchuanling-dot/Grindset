package com.grindset.backend.service.auth;

/**
 * 解密后的第三方登录客户端配置,登录流程只依赖这个快照。
 */
public record OAuthClientConfig(String provider,
                                String clientId,
                                String clientSecret,
                                String redirectUri,
                                String frontendCallbackUri) {
}
