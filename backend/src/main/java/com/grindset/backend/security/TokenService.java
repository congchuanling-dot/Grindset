package com.grindset.backend.security;

import com.grindset.backend.config.JwtProperties;
import org.springframework.stereotype.Service;

/**
 * access + refresh 的组合操作:签发、轮换、吊销。
 * Controller 只依赖这个类,不关心两种 token 的细节。
 */
@Service
public class TokenService {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties properties;

    public TokenService(JwtService jwtService, RefreshTokenService refreshTokenService,
                        JwtProperties properties) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
    }

    /** 新登录/注册:签发一对 token */
    public TokenPair issue(Long userId) {
        return new TokenPair(
                jwtService.issue(userId),
                refreshTokenService.issue(userId),
                properties.accessTtlSeconds(),
                properties.refreshTtlSeconds());
    }

    /** 刷新:先消费旧 refresh,再签发一对新的(轮换) */
    public Rotation rotate(String oldRefreshToken) {
        Long userId = refreshTokenService.consume(oldRefreshToken);
        return new Rotation(userId, issue(userId));
    }

    public void revoke(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public record TokenPair(String accessToken, String refreshToken,
                            long accessExpiresIn, long refreshExpiresIn) {
    }

    public record Rotation(Long userId, TokenPair tokens) {
    }
}
