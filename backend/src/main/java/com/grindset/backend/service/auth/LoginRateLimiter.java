package com.grindset.backend.service.auth;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 登录失败限流:双桶加权的近似滑动窗口。
 * 账号 5 次/分钟、来源 IP 30 次/分钟,只统计密码错误的尝试,成功登录不计数。
 * 每次请求在 BCrypt 校验前先估算,超限直接 429,避免 100ms/次的 CPU 被爆破放大。
 * Redis 异常时降级放行(fail-open),不因为限流组件拖垮登录链路。
 */
@Service
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final String ACCOUNT_PREFIX = "auth:rl:login:acct:";
    private static final String IP_PREFIX = "auth:rl:login:ip:";

    /** 窗口 60s 拆成两个 30s 的桶,跨桶边界不会出现两倍突刺 */
    private static final long BUCKET_SECONDS = 30;
    /** 上一个桶最晚在创建约 2*T 后仍会被读取,多留 60s 避免边界过期 */
    private static final long TTL_MILLIS = (BUCKET_SECONDS * 2 + 60) * 1000;

    private static final long ACCOUNT_LIMIT = 5;
    private static final long IP_LIMIT = 30;

    /** 只读估算:上一桶次数按「剩余时间比例」衰减,刚切桶时权重为 1,桶末尾趋近 0 */
    private static final RedisScript<Long> ESTIMATE =
            RedisScript.of(new ClassPathResource("lua/login_rate_limit_estimate.lua"), Long.class);

    /** 记一次失败:原子 INCR,首次写入时设 TTL,避免每次续命导致 key 永不过期 */
    private static final RedisScript<Long> RECORD =
            RedisScript.of(new ClassPathResource("lua/login_rate_limit_record.lua"), Long.class);

    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 登录前检查。超限抛 429;Redis 不可用只记警告并放行,
     * 避免限流组件成为登录链路的单点故障。
     */
    public void check(String account, String ip) {
        long now = Instant.now().getEpochSecond();
        try {
            if (estimate(ACCOUNT_PREFIX + accountKey(account), now) >= ACCOUNT_LIMIT) {
                throw tooFrequent();
            }
            if (ip != null && !ip.isBlank() && estimate(IP_PREFIX + ip, now) >= IP_LIMIT) {
                throw tooFrequent();
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("登录限流检查失败,降级放行: {}", e.toString());
        }
    }

    /** 密码校验失败后计数。计数失败不影响主流程,只记日志。 */
    public void recordFailure(String account, String ip) {
        long now = Instant.now().getEpochSecond();
        try {
            record(ACCOUNT_PREFIX + accountKey(account), now);
            if (ip != null && !ip.isBlank()) {
                record(IP_PREFIX + ip, now);
            }
        } catch (RuntimeException e) {
            log.warn("登录失败计数写入失败: {}", e.toString());
        }
    }

    private long estimate(String prefix, long now) {
        long bucket = now / BUCKET_SECONDS;
        Long value = redis.execute(ESTIMATE,
                List.of(prefix + ":" + (bucket - 1), prefix + ":" + bucket),
                String.valueOf(BUCKET_SECONDS), String.valueOf(now));
        return value == null ? 0 : value;
    }

    private void record(String prefix, long now) {
        long bucket = now / BUCKET_SECONDS;
        redis.execute(RECORD, List.of(prefix + ":" + bucket), String.valueOf(TTL_MILLIS));
    }

    private static BizException tooFrequent() {
        return new BizException(ErrorCode.TOO_MANY_REQUESTS, "尝试过于频繁，请 1 分钟后再试");
    }

    /**
     * 账号做 SHA-256,Redis 里不落明文邮箱。
     * trim + lowercase 与库里 utf8mb4_0900_ai_ci 的大小写不敏感匹配保持一致,
     * 否则同一账号可用大小写变体绕开计数。
     */
    private static String accountKey(String account) {
        String normalized = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
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
