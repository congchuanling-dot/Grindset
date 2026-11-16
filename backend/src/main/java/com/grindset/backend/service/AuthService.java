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

import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final String PROVIDER_LOCAL = "LOCAL";

    /** 与 users.nickname VARCHAR(64) 对齐,超长会在写库时被截断报错 */
    private static final int MAX_ACCOUNT_LENGTH = 64;

    /** 账号统一用邮箱:本地部分@域名.后缀,够 MVP 用,不做 RFC 全量校验 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 密码 8~16 位,且必须同时含字母和数字 */
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 16;
    private static final Pattern PASSWORD_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile("[0-9]");

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
            throw new BizException(409, "该邮箱已存在");
        }

        // 建用户
        User user = new User();
        user.setId(idGenerator.nextId());
        user.setNickname(account);
        user.setEmail(account);
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
            throw new BizException(409, "该邮箱已存在");
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

    /**
     * 取当前登录用户。用户不存在或已禁用时抛业务异常,
     * 供 /api/auth/me、简历等需要用户资料的接口复用。
     */
    public User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在，请重新登录");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException(403, "账号已被禁用");
        }
        return user;
    }

    /** 按账号名查 LOCAL 身份,查不到返回 null */
    private UserIdentity findLocalIdentity(String account) {
        QueryWrapper<UserIdentity> q = new QueryWrapper<>();
        q.eq("provider", PROVIDER_LOCAL).eq("identifier", account);
        return identityMapper.selectOne(q);
    }

    /**
     * 注册/登录共用的入参校验:账号必须是邮箱,密码 8~16 位且同时含字母和数字。
     */
    private void validateCredentials(String account, String rawPassword) {
        if (account == null || account.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(400, "邮箱和密码不能为空");
        }
        if (account.length() > MAX_ACCOUNT_LENGTH) {
            throw new BizException(400, "邮箱长度不能超过 " + MAX_ACCOUNT_LENGTH + " 个字符");
        }
        if (!EMAIL_PATTERN.matcher(account).matches()) {
            throw new BizException(400, "请输入正确的邮箱地址");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new BizException(400,
                    "密码长度需为 " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH + " 位");
        }
        if (!PASSWORD_LETTER.matcher(rawPassword).find() || !PASSWORD_DIGIT.matcher(rawPassword).find()) {
            throw new BizException(400, "密码必须同时包含字母和数字");
        }
    }
}
