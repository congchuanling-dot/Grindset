package com.grindset.backend.security;

import com.grindset.backend.common.BizException;
import com.grindset.backend.config.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * refresh token 管理。
 * token 本身是 32 字节随机串,Redis 只存它的 SHA-256 -> userId,TTL 到期自动清理。
 * 消费用 GETDEL 保证同一个 token 只能换一次(轮换)。
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenService(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.ttl = Duration.ofDays(properties.getRefreshTtlDays());
    }

    /** 生成并存储 refresh token,返回给调用方写 Cookie 的原始串 */
    public String issue(Long userId) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        redis.opsForValue().set(key(token), String.valueOf(userId), ttl);
        return token;
    }

    /** 原子消费:成功返回 userId,不存在/已用过抛 401 */
    public Long consume(String token) {
        String value = redis.opsForValue().getAndDelete(key(token));
        if (value == null) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        return Long.valueOf(value);
    }

    /** 登出时主动删除 */
    public void revoke(String token) {
        redis.delete(key(token));
    }

    private static String key(String token) {
        return KEY_PREFIX + sha256Hex(token);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
