package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.grindset.backend.common.StorageProvider;

import java.time.LocalDateTime;

/**
 * storage_config 表:外部对象存储配置(简历原件等)。
 * AccessKey 只存密文,展示和接口只返回掩码;主密钥来自 SECRET_MASTER_KEY。
 */
@TableName("storage_config")
public class StorageConfig {

    /** 加密 AAD 绑定此 id,修改 id 会导致密文解不开 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 目前仅 ALIYUN_OSS */
    private StorageProvider provider;

    private String endpoint;

    private String bucket;

    private String dirPrefix;

    /** 签名 URL 有效期(秒) */
    private Integer urlExpireSeconds;

    /** AES-GCM 密文,v1:base64(iv+密文+tag) */
    private String accessKeyIdCipher;

    /** AES-GCM 密文,v1:base64(iv+密文+tag) */
    private String accessKeySecretCipher;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StorageProvider getProvider() {
        return provider;
    }

    public void setProvider(StorageProvider provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getDirPrefix() {
        return dirPrefix;
    }

    public void setDirPrefix(String dirPrefix) {
        this.dirPrefix = dirPrefix;
    }

    public Integer getUrlExpireSeconds() {
        return urlExpireSeconds;
    }

    public void setUrlExpireSeconds(Integer urlExpireSeconds) {
        this.urlExpireSeconds = urlExpireSeconds;
    }

    public String getAccessKeyIdCipher() {
        return accessKeyIdCipher;
    }

    public void setAccessKeyIdCipher(String accessKeyIdCipher) {
        this.accessKeyIdCipher = accessKeyIdCipher;
    }

    public String getAccessKeySecretCipher() {
        return accessKeySecretCipher;
    }

    public void setAccessKeySecretCipher(String accessKeySecretCipher) {
        this.accessKeySecretCipher = accessKeySecretCipher;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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
