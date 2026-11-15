-- ============================================================
-- 登录模块建表脚本
-- 库:grindset   引擎:InnoDB   字符集:utf8mb4
-- 设计说明见 docs/登录模块-技术设计.md
-- 主键为雪花算法生成的 BIGINT,由 Java 代码写入(非数据库自增)
-- ============================================================

CREATE DATABASE IF NOT EXISTS grindset
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE grindset;

-- ---------- 用户表:一个人的基本信息 ----------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT        NOT NULL COMMENT '主键,雪花 ID(由应用生成)',
    nickname    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '展示名',
    avatar_url  VARCHAR(512)  NULL COMMENT '头像 URL,OAuth 登录可带',
    email       VARCHAR(255)  NULL COMMENT '邮箱,仅展示用,不唯一、不验证',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态:0 正常 / 1 禁用',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基本信息';

-- ---------- 身份表:一个人的某种登录方式(可多条) ----------
CREATE TABLE IF NOT EXISTS user_identities (
    id          BIGINT        NOT NULL COMMENT '主键,雪花 ID(由应用生成)',
    user_id     BIGINT        NOT NULL COMMENT '关联 users.id(无物理外键,靠代码保证)',
    provider    VARCHAR(32)   NOT NULL COMMENT '登录方式:LOCAL/GITHUB/GOOGLE/EMAIL_CODE',
    identifier  VARCHAR(255)  NOT NULL COMMENT 'LOCAL=账号名;OAuth=对方唯一 id(sub)',
    credential  VARCHAR(255)  NULL COMMENT '仅 LOCAL 有值,存 BCrypt 密码哈希',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_identifier (provider, identifier) COMMENT '同一登录方式下标识唯一,防重复注册/并发',
    KEY idx_user_id (user_id) COMMENT '按用户查其所有登录方式'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录身份(多身份模型)';
