package com.grindset.backend.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.auth.AuthResult;
import com.grindset.backend.entity.auth.User;
import com.grindset.backend.entity.auth.UserIdentity;
import com.grindset.backend.mapper.auth.UserMapper;
import com.grindset.backend.security.TokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * 第三方登录的公共编排:state 防 CSRF、一次性 code 暂存、绑定/合并/建号、签发本系统令牌。
 * GitHub / Google 只是获取用户信息的方式不同,登录规则必须保持一致,因此统一收敛到这里。
 */
@Service
public class SocialLoginService {

    private static final String STATE_KEY_PREFIX = "oauth:%s:state:";
    private static final String CODE_KEY_PREFIX = "oauth:%s:code:";
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_AVATAR_LENGTH = 512;

    private final IdentityService identityService;
    private final AuthService authService;
    private final TokenService tokenService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public SocialLoginService(IdentityService identityService,
                              AuthService authService,
                              TokenService tokenService,
                              StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              UserMapper userMapper,
                              SnowflakeIdGenerator idGenerator,
                              TransactionTemplate transactionTemplate) {
        this.identityService = identityService;
        this.authService = authService;
        this.tokenService = tokenService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 生成 state 并写入 Redis。
     *
     * @param provider    小写渠道名,如 github / google,用于拼 Redis key
     * @param ttlMinutes  有效期(分钟)
     */
    public String createState(String provider, long ttlMinutes) {
        String state = randomToken();
        redis.opsForValue().set(stateKey(provider, state), "1", Duration.ofMinutes(ttlMinutes));
        return state;
    }

    /** 校验并消费 state:必须是本系统发出且未使用过的 */
    public void verifyState(String provider, String state) {
        if (state == null || state.isBlank()
                || redis.opsForValue().getAndDelete(stateKey(provider, state)) == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "state 已失效，请重新登录");
        }
    }

    /**
     * 登录成功后的收尾:绑定或建号 → 签发令牌 → 暂存一次性 code。
     * 事务只包数据库写入,第三方 HTTP 调用已在事务外完成。
     */
    public CallbackResult completeLogin(String provider, SocialUser socialUser, long codeTtlSeconds) {
        String identityProvider = provider.toUpperCase(Locale.ROOT);
        Long userId = transactionTemplate.execute(status -> loginOrRegister(identityProvider, socialUser));
        if (userId == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "登录失败，请稍后重试");
        }

        User user = authService.requireActiveUser(userId);
        TokenService.TokenPair tokens = tokenService.issue(userId);
        AuthResult result = new AuthResult(String.valueOf(userId), tokens.accessToken(),
                tokens.accessExpiresIn(), user.getNickname());
        return new CallbackResult(stash(provider, result, codeTtlSeconds), tokens.refreshToken());
    }

    /** 前端用一次性 code 换本系统令牌,code 用过即废 */
    public AuthResult consume(String provider, String oneTimeCode) {
        if (oneTimeCode == null || oneTimeCode.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少登录凭证");
        }
        String json = redis.opsForValue().getAndDelete(codeKey(provider, oneTimeCode));
        if (json == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        try {
            StoredLogin stored = objectMapper.readValue(json, StoredLogin.class);
            return new AuthResult(stored.userId(), stored.accessToken(),
                    stored.expiresIn(), stored.nickname());
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "登录结果解析失败");
        }
    }

    /**
     * 已绑定直接复用;邮箱已验证且命中本地账号则自动合并;否则建新用户。
     * 未验证邮箱不参与合并,避免冒绑他人账号。
     */
    private Long loginOrRegister(String identityProvider, SocialUser socialUser) {
        UserIdentity bound = identityService.find(identityProvider, socialUser.identifier());
        if (bound != null) {
            return bound.getUserId();
        }

        if (socialUser.emailVerified()
                && socialUser.email() != null && !socialUser.email().isBlank()) {
            UserIdentity local = identityService.findLocal(socialUser.email());
            if (local != null) {
                return identityService.bind(local.getUserId(),
                        identityProvider, socialUser.identifier());
            }
        }

        User user = new User();
        user.setId(idGenerator.nextId());
        user.setNickname(truncate(resolveNickname(socialUser), MAX_NICKNAME_LENGTH));
        user.setEmail(truncate(socialUser.email(), MAX_EMAIL_LENGTH));
        user.setAvatarUrl(truncate(socialUser.avatarUrl(), MAX_AVATAR_LENGTH));
        user.setStatus(0);
        userMapper.insert(user);
        return identityService.bind(user.getId(), identityProvider, socialUser.identifier());
    }

    private String stash(String provider, AuthResult result, long codeTtlSeconds) {
        String oneTimeCode = randomToken();
        StoredLogin stored = new StoredLogin(result.getUserId(), result.getAccessToken(),
                result.getExpiresIn(), result.getNickname());
        try {
            redis.opsForValue().set(codeKey(provider, oneTimeCode),
                    objectMapper.writeValueAsString(stored),
                    Duration.ofSeconds(codeTtlSeconds));
            return oneTimeCode;
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "登录结果暂存失败");
        }
    }

    private static String resolveNickname(SocialUser socialUser) {
        if (socialUser.nickname() != null && !socialUser.nickname().isBlank()) {
            return socialUser.nickname();
        }
        if (socialUser.email() != null && socialUser.email().contains("@")) {
            return socialUser.email().substring(0, socialUser.email().indexOf('@'));
        }
        return "新用户";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String stateKey(String provider, String state) {
        return String.format(STATE_KEY_PREFIX, provider) + state;
    }

    private static String codeKey(String provider, String code) {
        return String.format(CODE_KEY_PREFIX, provider) + code;
    }

    private static String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 回调结果:oneTimeCode 交给前端,refreshToken 由 Controller 写 Cookie */
    public record CallbackResult(String oneTimeCode, String refreshToken) {
    }

    /** 各渠道归一化后的用户信息,登录规则只认这几个字段 */
    public record SocialUser(String identifier, String nickname, String email,
                             boolean emailVerified, String avatarUrl) {
    }

    /** Redis 里暂存的登录结果,字段名与 AuthResult 保持一致 */
    private record StoredLogin(String userId, String accessToken, long expiresIn, String nickname) {
    }
}
