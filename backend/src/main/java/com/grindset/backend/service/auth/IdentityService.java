package com.grindset.backend.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.entity.auth.UserIdentity;
import com.grindset.backend.mapper.auth.UserIdentityMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * user_identities 的唯一读写口。
 * 本地账号的查重/建身份走这里,以后 GitHub 等第三方登录复用同一套查询与绑定逻辑。
 */
@Service
public class IdentityService {

    /** 账号密码登录 */
    public static final String PROVIDER_LOCAL = "LOCAL";

    private final UserIdentityMapper identityMapper;
    private final SnowflakeIdGenerator idGenerator;

    public IdentityService(UserIdentityMapper identityMapper, SnowflakeIdGenerator idGenerator) {
        this.identityMapper = identityMapper;
        this.idGenerator = idGenerator;
    }

    /** 按登录方式 + 标识查身份,查不到返回 null */
    public UserIdentity find(String provider, String identifier) {
        QueryWrapper<UserIdentity> q = new QueryWrapper<>();
        q.eq("provider", provider).eq("identifier", identifier);
        return identityMapper.selectOne(q);
    }

    /** 按邮箱查本地账号身份 */
    public UserIdentity findLocal(String account) {
        return find(PROVIDER_LOCAL, account);
    }

    /**
     * 新建 LOCAL 身份,密码由调用方先做 BCrypt。
     * 唯一索引冲突说明账号已被注册(并发下的最后防线)。
     */
    public void createLocal(Long userId, String account, String passwordHash) {
        UserIdentity identity = new UserIdentity();
        identity.setId(idGenerator.nextId());
        identity.setUserId(userId);
        identity.setProvider(PROVIDER_LOCAL);
        identity.setIdentifier(account);
        identity.setCredential(passwordHash);
        try {
            identityMapper.insert(identity);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "该邮箱已存在");
        }
    }
}
