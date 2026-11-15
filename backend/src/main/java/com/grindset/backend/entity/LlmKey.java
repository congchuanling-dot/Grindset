package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * llm_keys 表:一个渠道下的 API Key 池。key 只存密文,前端/接口只看到掩码。
 * status:0 正常 / 1 鉴权失败被自动停用 / 2 手动禁用。
 */
@TableName("llm_keys")
public class LlmKey {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long channelId;

    /** AES-GCM 密文,v1:base64(iv+密文+tag) */
    private String keyCipher;

    /** 展示用掩码 */
    private String keyMask;

    private Integer status;

    /** 连续失败次数,用于计算冷却时间 */
    private Integer failCount;

    /** 冷却结束时间,期间不参与调度 */
    private LocalDateTime cooldownUntil;

    /** 最后使用时间,按最久未用优先轮询 */
    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public String getKeyCipher() {
        return keyCipher;
    }

    public void setKeyCipher(String keyCipher) {
        this.keyCipher = keyCipher;
    }

    public String getKeyMask() {
        return keyMask;
    }

    public void setKeyMask(String keyMask) {
        this.keyMask = keyMask;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public LocalDateTime getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(LocalDateTime cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
