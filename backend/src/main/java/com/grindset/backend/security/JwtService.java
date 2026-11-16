package com.grindset.backend.security;

import com.grindset.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * access token 的签发与校验。
 * 只做无状态验签,不查库;refresh token 不在这里,见 RefreshTokenService。
 */
@Component
public class JwtService {

    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 字节(当前 " + secret.length + ")");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtlSeconds = properties.accessTtlSeconds();
    }

    /** 签发 access token,subject 放 userId */
    public String issue(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析出 userId。
     * 签名不对、过期、类型不对都抛 JwtException。
     */
    public Long parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TYPE_ACCESS.equals(claims.get("typ", String.class))) {
            throw new JwtException("token 类型错误");
        }
        return Long.valueOf(claims.getSubject());
    }
}
