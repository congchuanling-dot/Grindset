package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.entity.User;
import com.grindset.backend.entity.UserIdentity;
import com.grindset.backend.mapper.UserIdentityMapper;
import com.grindset.backend.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private static final String PROVIDER_LOCAL = "LOCAL";

    /** 与 users.nickname VARCHAR(64) 对齐,超长会在写库时被截断报错 */
    private static final int MAX_ACCOUNT_LENGTH = 64;

    /** BCrypt 只处理前 72 字节,超出部分被静默忽略,必须提前拒绝 */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserMapper userMapper;
    private final UserIdentityMapper identityMapper;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator idGenerator;

    public AuthService(UserMapper userMapper, UserIdentityMapper identityMapper,
                       PasswordEncoder passwordEncoder, SnowflakeIdGenerator idGenerator) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
    }

    /**
     * 账号密码注册。建 user + 一条 LOCAL identity。
     * @return 新建用户的 id
     */
    @Transactional
    public Long register(String account, String rawPassword) {
        validateCredentials(account, rawPassword);

        // 先查一次,给出友好提示(唯一索引是并发下的最后防线)
        if (findLocalIdentity(account) != null) {
            throw new BizException(409, "该账号已存在");
        }

        // 建用户
        User user = new User();
        user.setId(idGenerator.nextId());
        user.setNickname(account);
        user.setStatus(0);
        userMapper.insert(user);

        // 建 LOCAL 登录身份,密码 BCrypt 加密后存
        UserIdentity identity = new UserIdentity();
        identity.setId(idGenerator.nextId());
        identity.setUserId(user.getId());
        identity.setProvider(PROVIDER_LOCAL);
        identity.setIdentifier(account);
        identity.setCredential(passwordEncoder.encode(rawPassword));
        try {
            identityMapper.insert(identity);
        } catch (DuplicateKeyException e) {
            // 并发下两个请求都通过了上面的查重,唯一索引在此兜底
            throw new BizException(409, "该账号已存在");
        }

        return user.getId();
    }

    /**
     * 账号密码登录。校验通过返回对应 userId。
     */
    public Long login(String account, String rawPassword) {
        validateCredentials(account, rawPassword);

        UserIdentity identity = findLocalIdentity(account);
        // 不区分"账号不存在"和"密码错",统一提示,防账号枚举
        if (identity == null || !passwordEncoder.matches(rawPassword, identity.getCredential())) {
            throw new BizException(401, "账号或密码错误");
        }
        return identity.getUserId();
    }

    /** 按账号名查 LOCAL 身份,查不到返回 null */
    private UserIdentity findLocalIdentity(String account) {
        QueryWrapper<UserIdentity> q = new QueryWrapper<>();
        q.eq("provider", PROVIDER_LOCAL).eq("identifier", account);
        return identityMapper.selectOne(q);
    }

    /**
     * 注册/登录共用的入参校验。
     * 密码按 UTF-8 字节数限制在 72 以内,避免 BCrypt 静默截断导致"不同密码也能登录"。
     */
    private void validateCredentials(String account, String rawPassword) {
        if (account == null || account.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(400, "账号和密码不能为空");
        }
        if (account.length() > MAX_ACCOUNT_LENGTH) {
            throw new BizException(400, "账号长度不能超过 " + MAX_ACCOUNT_LENGTH + " 个字符");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BizException(400, "密码过长,不能超过 " + MAX_PASSWORD_BYTES + " 字节(中文约 24 个字)");
        }
    }
}
