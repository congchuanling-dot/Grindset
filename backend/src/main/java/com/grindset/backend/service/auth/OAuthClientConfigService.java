package com.grindset.backend.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.entity.auth.OAuthClientConfigEntity;
import com.grindset.backend.llm.SecretCipher;
import com.grindset.backend.mapper.auth.OAuthClientConfigMapper;
import org.springframework.stereotype.Service;

import static com.grindset.backend.common.constant.OAuthClientStatus.ENABLED;

/**
 * 从数据库读取第三方登录配置,并将 client_secret 解密。
 * 每次登录请求直接查库,改库即时生效,不做缓存。
 */
@Service
public class OAuthClientConfigService {

    private final OAuthClientConfigMapper mapper;
    private final SecretCipher secretCipher;

    public OAuthClientConfigService(OAuthClientConfigMapper mapper, SecretCipher secretCipher) {
        this.mapper = mapper;
        this.secretCipher = secretCipher;
    }

    /**
     * 读取并解密指定渠道的配置,缺失或停用时直接报错。
     * 密文 AAD 绑定行 id,搬运密文到其他行会解密失败。
     */
    public OAuthClientConfig require(String provider) {
        OAuthClientConfigEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<OAuthClientConfigEntity>()
                        .eq(OAuthClientConfigEntity::getProvider, provider)
                        .eq(OAuthClientConfigEntity::getStatus, ENABLED));
        if (entity == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, provider + " 登录未配置");
        }
        String clientSecret = secretCipher.decrypt(entity.getClientSecret(), entity.getId());
        return new OAuthClientConfig(entity.getProvider(), entity.getClientId(), clientSecret,
                entity.getRedirectUri(), entity.getFrontendCallbackUri());
    }
}
