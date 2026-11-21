-- 第三方登录(OAuth)客户端配置。
-- client_secret 使用 SecretCipher 加密存储,密文以 v1: 开头,AAD 绑定本行 id。
-- 修改密文时必须使用同一个主密钥(SECRET_MASTER_KEY,未配置时由 app.jwt.secret 派生),否则解不开。
CREATE TABLE IF NOT EXISTS oauth_client_config (
    id                    BIGINT       NOT NULL COMMENT '雪花主键,同时作为密文 AAD',
    provider              VARCHAR(32)  NOT NULL COMMENT '渠道:github / google',
    client_id             VARCHAR(255) NOT NULL COMMENT 'OAuth Client ID(非敏感)',
    client_secret         VARCHAR(1024) NOT NULL COMMENT 'AES-GCM 密文,以 v1: 开头',
    redirect_uri          VARCHAR(512) NOT NULL COMMENT '回调地址,必须与平台后台登记一致',
    frontend_callback_uri VARCHAR(512) NOT NULL COMMENT '后端回调后跳转的前端页面',
    status                TINYINT      NOT NULL DEFAULT 0 COMMENT '0 启用 / 1 停用',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_provider (provider)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='第三方登录客户端配置';
