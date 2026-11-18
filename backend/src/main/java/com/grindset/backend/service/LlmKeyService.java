package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.entity.LlmKey;
import com.grindset.backend.llm.SecretCipher;
import com.grindset.backend.mapper.LlmKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Key 池维护:加密入库、启停、以及调度后的健康状态回写。
 * 只有这里和 ModelCatalogService 会碰密文,其余代码只见掩码。
 */
@Service
public class LlmKeyService {

    private static final Logger log = LoggerFactory.getLogger(LlmKeyService.class);

    public static final int STATUS_NORMAL = 0;
    /** 鉴权失败(401/403)被自动停用 */
    public static final int STATUS_INVALID = 1;
    /** 手动禁用 */
    public static final int STATUS_DISABLED = 2;

    private final LlmKeyMapper keyMapper;
    private final SecretCipher cipher;
    private final SnowflakeIdGenerator idGenerator;

    public LlmKeyService(LlmKeyMapper keyMapper, SecretCipher cipher,
                         SnowflakeIdGenerator idGenerator) {
        this.keyMapper = keyMapper;
        this.cipher = cipher;
        this.idGenerator = idGenerator;
    }

    /** 新增一把 key,返回掩码;明文不返回、不落日志 */
    public String addKey(long channelId, String rawKey) {
        String key = rawKey == null ? "" : rawKey.strip();
        if (key.length() < 8) {
            throw new BizException(ErrorCode.BAD_REQUEST, "API Key 格式不正确");
        }
        LlmKey entity = new LlmKey();
        entity.setId(idGenerator.nextId());
        entity.setChannelId(channelId);
        entity.setKeyCipher(cipher.encrypt(key, channelId));
        entity.setKeyMask(SecretCipher.mask(key));
        entity.setStatus(STATUS_NORMAL);
        entity.setFailCount(0);
        keyMapper.insert(entity);
        return entity.getKeyMask();
    }

    /** 启用(0) / 手动禁用(2);启用时清掉失败次数和冷却 */
    public void updateStatus(long keyId, int status) {
        if (status != STATUS_NORMAL && status != STATUS_DISABLED) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只支持启用(0)或禁用(2)");
        }
        int updated = keyMapper.update(null, new UpdateWrapper<LlmKey>()
                .eq("id", keyId)
                .set("status", status)
                .set("fail_count", 0)
                .set("cooldown_until", null));
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Key 不存在");
        }
    }

    /** 调用成功:记最后使用时间,失败计数清零(最久未用优先,实现轮询) */
    public void markUsed(Long keyId) {
        if (keyId == null) {
            return;
        }
        keyMapper.update(null, new UpdateWrapper<LlmKey>()
                .eq("id", keyId)
                .set("last_used_at", LocalDateTime.now())
                .set("fail_count", 0));
    }

    /**
     * 调用失败:鉴权类错误直接停用;其余按次数指数冷却,期间不参与调度。
     * 冷却上限 10 分钟,避免一把坏 key 一直打。
     */
    public void markFailed(Long keyId, Integer upstreamStatus) {
        if (keyId == null) {
            return;
        }
        LlmKey key = keyMapper.selectById(keyId);
        if (key == null) {
            return;
        }
        int fails = (key.getFailCount() == null ? 0 : key.getFailCount()) + 1;

        if (upstreamStatus != null && (upstreamStatus == 401 || upstreamStatus == 403)) {
            keyMapper.update(null, new UpdateWrapper<LlmKey>()
                    .eq("id", keyId)
                    .set("status", STATUS_INVALID)
                    .set("fail_count", fails)
                    .set("cooldown_until", null));
            log.warn("Key {} 鉴权失败已自动停用", keyId);
        } else {
            long cooldownSeconds = Math.min(600L, 30L << Math.min(fails - 1, 4));
            keyMapper.update(null, new UpdateWrapper<LlmKey>()
                    .eq("id", keyId)
                    .set("fail_count", fails)
                    .set("cooldown_until", LocalDateTime.now().plusSeconds(cooldownSeconds)));
            log.warn("Key {} 调用失败 {} 次,冷却 {} 秒", keyId, fails, cooldownSeconds);
        }
    }
}
