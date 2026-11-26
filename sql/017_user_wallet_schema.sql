-- ============================================================
-- 用户钱包 + 余额流水
-- 钱包与认证解耦:users 表只放资料,资金放 user_wallets(1:1)
-- 精度与 ai_usage_record.cost 对齐(DECIMAL(18,8)),避免小额调用被抹平
-- 脚本可重复执行
-- ============================================================

USE grindset;

CREATE TABLE IF NOT EXISTS user_wallets (
    id          BIGINT        NOT NULL COMMENT '主键,雪花 ID',
    user_id     BIGINT        NOT NULL COMMENT '关联 users.id',
    balance     DECIMAL(18,8) NOT NULL DEFAULT 1.00000000 COMMENT '余额,元',
    currency    VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id) COMMENT '一个用户一个钱包'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户钱包';

CREATE TABLE IF NOT EXISTS user_balance_log (
    id             BIGINT        NOT NULL COMMENT '主键,雪花 ID',
    user_id        BIGINT        NOT NULL COMMENT '关联 users.id',
    change_amount  DECIMAL(18,8) NOT NULL COMMENT '变动金额,正=入账 负=消费',
    balance_before DECIMAL(18,8) NOT NULL COMMENT '变动前余额',
    balance_after  DECIMAL(18,8) NOT NULL COMMENT '变动后余额',
    change_type    VARCHAR(16)   NOT NULL COMMENT 'INIT 初始化 / RECHARGE 充值 / CONSUME 消费 / ADJUST 调账',
    biz_type       VARCHAR(32)   NULL COMMENT '业务类型,取 AiBizType.name()',
    biz_ref        VARCHAR(64)   NULL COMMENT '业务单号:会话/面试/简历 ID',
    remark         VARCHAR(255)  NULL COMMENT '备注,调账用',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at) COMMENT '按用户倒序查流水',
    KEY idx_type_time (change_type, created_at) COMMENT '按类型统计'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户余额流水';
