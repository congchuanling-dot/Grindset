package com.grindset.backend.common;

/**
 * 外部对象存储提供方。目前只实现阿里云 OSS,
 * storage_config.provider 唯一键为后续扩展预留。
 */
public enum StorageProvider {

    ALIYUN_OSS
}
