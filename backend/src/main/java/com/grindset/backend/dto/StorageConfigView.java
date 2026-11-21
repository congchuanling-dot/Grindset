package com.grindset.backend.dto;

/**
 * 存储配置展示视图:AccessKey 只返回掩码,永不返回明文。
 * source:DB(数据库配置) / ENV(环境变量兜底) / NONE(未配置)。
 */
public record StorageConfigView(
        String id,
        String provider,
        String endpoint,
        String bucket,
        String dirPrefix,
        int urlExpireSeconds,
        boolean enabled,
        String accessKeyIdMask,
        boolean accessKeySecretConfigured,
        String source,
        String updatedAt) {
}
