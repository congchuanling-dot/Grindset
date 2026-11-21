package com.grindset.backend.dto;

/**
 * 存储配置更新请求。AccessKeyId / AccessKeySecret 都可为空:
 * 更新已有配置时留空表示保持原密文不变;首次创建时必须同时提供。
 */
public record StorageConfigUpdate(
        String endpoint,
        String bucket,
        String dirPrefix,
        Integer urlExpireSeconds,
        Boolean enabled,
        String accessKeyId,
        String accessKeySecret) {
}
