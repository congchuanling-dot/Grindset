package com.grindset.backend.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.config.GithubOAuthProperties;
import com.grindset.backend.dto.auth.AuthResult;
import com.grindset.backend.entity.auth.User;
import com.grindset.backend.entity.auth.UserIdentity;
import com.grindset.backend.mapper.auth.UserMapper;
import com.grindset.backend.security.TokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.UUID;

/**
 * GitHub 登录编排:
 * 生成 state → 换 access token → 拉用户与邮箱 → 绑定或建号 → 签发本系统令牌。
 */
@Service
public class GithubLoginService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String STATE_KEY_PREFIX = "oauth:github:state:";
    private static final String CODE_KEY_PREFIX = "oauth:github:code:";
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_AVATAR_LENGTH = 512;

    private final GithubOAuthProperties properties;
    private final GithubOAuthClient githubOAuthClient;
    private final IdentityService identityService;
    private final AuthService authService;
    private final TokenService tokenService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public GithubLoginService(GithubOAuthProperties properties,
                              GithubOAuthClient githubOAuthClient,
                              IdentityService identityService,
                              AuthService authService,
                              TokenService tokenService,
                              StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              UserMapper userMapper,
                              SnowflakeIdGenerator idGenerator,
                              TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.githubOAuthClient = githubOAuthClient;
        this.identityService = identityService;
        this.authService = authService;
        this.tokenService = tokenService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    /** 生成 GitHub 授权地址,state 存 Redis 防 CSRF */
    public String authorizeUrl() {
        if (!properties.isConfigured()) {
            throw new BizException(500, "GitHub 登录未配置");
        }
        String state = randomToken();
        redis.opsForValue().set(STATE_KEY_PREFIX + state, "1",
                Duration.ofMinutes(properties.getStateTtlMinutes()));
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", "read:user user:email")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    /**
     * 处理 GitHub 回调:校验 state、换 token、绑定或建号、签发本系统令牌。
     * 返回一次性 code(交给前端换令牌)与 refresh token(由 Controller 写入 Cookie)。
     */
    public CallbackResult handleCallback(String code, String state) {
        // state 一次性,校验后立即删除
        if (redis.opsForValue().getAndDelete(STATE_KEY_PREFIX + state) == null) {
            throw new BizException(400, "state 已失效，请重新登录");
        }
        String accessToken = githubOAuthClient.exchangeCode(code);
        GithubOAuthUser githubUser = githubOAuthClient.fetchUser(accessToken);
        Long userId = loginOrRegister(githubUser);

        User user = authService.requireActiveUser(userId);
        TokenService.TokenPair tokens = tokenService.issue(userId);
        AuthResult result = new AuthResult(String.valueOf(userId), tokens.accessToken(),
                tokens.accessExpiresIn(), user.getNickname());
        return new CallbackResult(stash(result), tokens.refreshToken());
    }

    /** 前端用一次性 code 换 access token */
    public AuthResult consume(String oneTimeCode) {
        if (oneTimeCode == null || oneTimeCode.isBlank()) {
            throw new BizException(400, "缺少登录凭证");
        }
        String json = redis.opsForValue().getAndDelete(CODE_KEY_PREFIX + oneTimeCode);
        if (json == null) {
            throw new BizException(401, "登录已过期，请重新登录");
        }
        try {
            StoredLogin stored = objectMapper.readValue(json, StoredLogin.class);
            return new AuthResult(stored.userId(), stored.accessToken(),
                    stored.expiresIn(), stored.nickname());
        } catch (JsonProcessingException e) {
            throw new BizException(500, "登录结果解析失败");
        }
    }

    /**
     * 已绑定直接复用;邮箱已验证且命中本地账号则自动合并;否则建新用户。
     * 事务只包裹数据库写入,HTTP 调用已在事务外完成。
     */
    private Long loginOrRegister(GithubOAuthUser githubUser) {
        return transactionTemplate.execute(status -> {
            UserIdentity bound = identityService.find(
                    IdentityService.PROVIDER_GITHUB, githubUser.githubId());
            if (bound != null) {
                return bound.getUserId();
            }

            // 邮箱相同则绑定到已有本地账号(仅使用已验证邮箱)
            if (githubUser.email() != null && !githubUser.email().isBlank()) {
                UserIdentity local = identityService.findLocal(githubUser.email());
                if (local != null) {
                    return identityService.bind(local.getUserId(),
                            IdentityService.PROVIDER_GITHUB, githubUser.githubId());
                }
            }

            User user = new User();
            user.setId(idGenerator.nextId());
            user.setNickname(truncate(pickNickname(githubUser), MAX_NICKNAME_LENGTH));
            user.setEmail(truncate(githubUser.email(), MAX_EMAIL_LENGTH));
            user.setAvatarUrl(truncate(githubUser.avatarUrl(), MAX_AVATAR_LENGTH));
            user.setStatus(0);
            userMapper.insert(user);
            return identityService.bind(user.getId(),
                    IdentityService.PROVIDER_GITHUB, githubUser.githubId());
        });
    }

    /** 登录结果暂存,前端凭一次性 code 取回 */
    private String stash(AuthResult result) {
        String oneTimeCode = randomToken();
        StoredLogin stored = new StoredLogin(result.getUserId(), result.getAccessToken(),
                result.getExpiresIn(), result.getNickname());
        try {
            redis.opsForValue().set(CODE_KEY_PREFIX + oneTimeCode,
                    objectMapper.writeValueAsString(stored),
                    Duration.ofSeconds(properties.getCodeTtlSeconds()));
            return oneTimeCode;
        } catch (JsonProcessingException e) {
            throw new BizException(500, "登录结果暂存失败");
        }
    }

    private static String pickNickname(GithubOAuthUser githubUser) {
        if (githubUser.nickname() != null && !githubUser.nickname().isBlank()) {
            return githubUser.nickname();
        }
        if (githubUser.login() != null && !githubUser.login().isBlank()) {
            return githubUser.login();
        }
        return "GitHub 用户";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 回调结果:oneTimeCode 交给前端,refreshToken 由 Controller 写 Cookie */
    public record CallbackResult(String oneTimeCode, String refreshToken) {
    }

    /** Redis 里暂存的登录结果,字段名与 AuthResult 保持一致 */
    private record StoredLogin(String userId, String accessToken, long expiresIn, String nickname) {
    }
}
