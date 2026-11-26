-- ============================================================
-- AI Token 用量记账
-- 1. ai_usage_record:每次模型调用的 token 用量与成本明细
-- 2. llm_models 增加单价列(元/千 token),用于计算成本
-- 脚本可重复执行
-- ============================================================

USE grindset;

CREATE TABLE IF NOT EXISTS ai_usage_record (
    id                 BIGINT        NOT NULL COMMENT '主键,雪花 ID',
    user_id            BIGINT        NOT NULL COMMENT '用户 ID',
    biz_type           VARCHAR(32)   NOT NULL COMMENT '业务类型:HOME_CHAT/AGENT_CHAT/INTERVIEW_QUESTION...',
    biz_ref            VARCHAR(64)   NULL COMMENT '业务单号:会话 ID / 面试 ID / 简历 ID',
    provider_id        VARCHAR(32)   NOT NULL COMMENT '厂商,如 deepseek',
    model_id           VARCHAR(128)  NOT NULL COMMENT '模型 ID,如 deepseek-chat',
    key_id             BIGINT        NULL COMMENT '使用的 key ID,便于和厂商账单对账',
    input_tokens       INT           NOT NULL DEFAULT 0 COMMENT '输入 token',
    output_tokens      INT           NOT NULL DEFAULT 0 COMMENT '输出 token',
    cached_tokens      INT           NOT NULL DEFAULT 0 COMMENT '输入中命中缓存的 token',
    estimated          TINYINT       NOT NULL DEFAULT 0 COMMENT '1=token 为字符估算值',
    input_price        DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT '输入单价,元/千 token(记录时快照)',
    cached_input_price DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT '缓存命中输入单价,元/千 token',
    output_price       DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT '输出单价,元/千 token',
    cost               DECIMAL(18,8) NOT NULL DEFAULT 0 COMMENT '本次成本,元',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at) COMMENT '用户用量查询',
    KEY idx_biz_time (biz_type, created_at) COMMENT '按业务类型聚合',
    KEY idx_model_time (provider_id, model_id, created_at) COMMENT '按模型聚合'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 模型调用用量明细';

-- ---------- llm_models 增加单价列(动态加列,可重复执行) ----------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'llm_models' AND COLUMN_NAME = 'input_price');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE llm_models ADD COLUMN input_price DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT ''输入单价,元/千token'' AFTER sort',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'llm_models' AND COLUMN_NAME = 'cached_input_price');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE llm_models ADD COLUMN cached_input_price DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT ''缓存命中输入单价,元/千token'' AFTER input_price',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'llm_models' AND COLUMN_NAME = 'output_price');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE llm_models ADD COLUMN output_price DECIMAL(12,6) NOT NULL DEFAULT 0 COMMENT ''输出单价,元/千token'' AFTER cached_input_price',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 单价示例(按厂商官网填写,单位为 元/千 token;不填则成本记 0,只有 token 统计)
-- UPDATE llm_models SET input_price = 0.002, output_price = 0.008 WHERE model_id = 'deepseek-chat';
