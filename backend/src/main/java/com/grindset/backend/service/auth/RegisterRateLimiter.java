package com.grindset.backend.service.auth;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 注册限流:按来源 IP 限制 10 次/小时,双桶加权近似滑动窗口。
 * 与登录不同,注册统计的是「尝试次数」而不是失败次数(受攻击目标是批量建号),
 * 因此检查通过就在同一次 Lua 里累加,避免 check-then-act 的超发窗口。
 * Redis 异常时降级放行,不让限流组件阻断注册。
 */
@Service
public class RegisterRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RegisterRateLimiter.class);

    private static final String IP_PREFIX = "auth:rl:register:ip:";

    /** 窗口 1 小时拆成两个 30 分钟的桶 */
    private static final long BUCKET_SECONDS = 1800;
    /** 上一个桶最晚在创建约 2*T 后仍会被读取,多留 60s 避免边界过期 */
    private static final long TTL_MILLIS = (BUCKET_SECONDS * 2 + 60) * 1000;

    private static final long LIMIT = 10;

    private static final RedisScript<Long> CHECK =
            RedisScript.of(new ClassPathResource("lua/register_rate_limit_check.lua"), Long.class);

    private final StringRedisTemplate redis;

    public RegisterRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 检查并计一次注册尝试。超限抛 429;Redis 不可用只记警告并放行。
     */
    public void check(String clientIp) {
        long now = Instant.now().getEpochSecond();
        try {
            long bucket = now / BUCKET_SECONDS;
            Long result = redis.execute(CHECK,
                    List.of(IP_PREFIX + clientIp + ":" + (bucket - 1), IP_PREFIX + clientIp + ":" + bucket),
                    String.valueOf(BUCKET_SECONDS), String.valueOf(now),
                    String.valueOf(LIMIT), String.valueOf(TTL_MILLIS));
            if (result != null && result < 0) {
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "注册过于频繁，请稍后再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("注册限流检查失败,降级放行: {}", e.toString());
        }
    }
}
