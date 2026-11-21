-- ============================================================
-- 外部对象存储配置（简历原件等）
-- 凭证使用 AES-GCM 密文存储,AAD 绑定行 id;主密钥来自 SECRET_MASTER_KEY
-- 一个 provider 只保留一行,目前仅 ALIYUN_OSS
-- 脚本可重复执行
-- ============================================================

USE grindset;

CREATE TABLE IF NOT EXISTS storage_config (
    id                       BIGINT       NOT NULL COMMENT '主键，雪花 ID（加密 AAD 绑定此值，勿修改）',
    provider                 VARCHAR(32)  NOT NULL COMMENT '存储提供方，目前仅 ALIYUN_OSS',
    endpoint                 VARCHAR(255) NOT NULL COMMENT '如 https://oss-cn-hangzhou.aliyuncs.com',
    bucket                   VARCHAR(128) NOT NULL COMMENT 'Bucket 名称（私有读）',
    dir_prefix               VARCHAR(128) NOT NULL DEFAULT 'resumes' COMMENT '对象 key 前缀',
    url_expire_seconds       INT          NOT NULL DEFAULT 3600 COMMENT '签名 URL 有效期（秒）',
    access_key_id_cipher     TEXT         NOT NULL COMMENT 'AccessKeyId 密文，AAD=行 id',
    access_key_secret_cipher TEXT         NOT NULL COMMENT 'AccessKeySecret 密文，AAD=行 id',
    enabled                  TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部存储凭证（密文）';
