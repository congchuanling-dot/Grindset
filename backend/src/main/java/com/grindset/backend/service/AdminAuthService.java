package com.grindset.backend.service;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 管理后台登录:只认配置里指定的那一个账号密码。
 *
 * <p>登录成功后签发一个随机会话令牌,存 Redis(默认 12 小时),
 * 前端后续请求放在 X-Admin-Token 里。与普通用户的 JWT 完全隔离,
 * 普通账号即使登录了业务端也无法进管理后台。</p>
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private static final String SESSION_PREFIX = "admin:session:";
    private static final Duration SESSION_TTL = Duration.ofHours(12);

    private final String account;
    private final String password;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public AdminAuthService(@Value("${app.admin.account:}") String account,
                            @Value("${app.admin.password:}") String password,
                            StringRedisTemplate redis) {
        this.account = account == null ? "" : account.strip();
        this.password = password == null ? "" : password;
        this.redis = redis;
    }

    /**
     * 校验管理员账号密码,通过返回会话令牌。
     * 账号不区分大小写,密码按原文比对,比对使用常量时间,避免时序侧信道。
     */
    public String login(String inputAccount, String inputPassword) {
        String expectedAccount = normalize(account);
        String actualAccount = normalize(inputAccount);
        boolean accountOk = !expectedAccount.isEmpty()
                && constantTimeEquals(expectedAccount, actualAccount);
        boolean passwordOk = !password.isEmpty()
                && constantTimeEquals(password, inputPassword == null ? "" : inputPassword);
        if (!accountOk || !passwordOk) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "管理员账号或密码错误");
        }

        String token = randomToken();
        redis.opsForValue().set(SESSION_PREFIX + token, "1", SESSION_TTL);
        return token;
    }

    /** 会话令牌是否有效;Redis 异常按无效处理,不放大成 500 */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(SESSION_PREFIX + token));
        } catch (RuntimeException e) {
            log.warn("管理会话校验失败: {}", e.toString());
            return false;
        }
    }

    /** 退出登录:删掉会话令牌,失败只记日志 */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            redis.delete(SESSION_PREFIX + token);
        } catch (RuntimeException e) {
            log.warn("管理会话删除失败: {}", e.toString());
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
