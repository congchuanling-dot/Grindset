-- ============================================================
-- 编程题（力扣式刷题）建表脚本
-- question_bank           增加 judge_mode / starter_code / 时限与内存限制
-- question_test_cases     编程题用例（is_sample=1 为例题，下发给前端）
-- question_submissions    代码提交与判题结果
-- 脚本可重复执行
-- ============================================================

USE grindset;

-- ---------- question_bank 扩展列 ----------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'judge_mode');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN judge_mode VARCHAR(8) NOT NULL DEFAULT ''TEXT'' COMMENT ''TEXT 问答题 / CODING 编程题'' AFTER type',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'starter_code');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN starter_code JSON NULL COMMENT ''各语言初始代码 {"JAVA":"...","PYTHON":"..."}'' AFTER reference_answer',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'time_limit_ms');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN time_limit_ms INT NOT NULL DEFAULT 2000 COMMENT ''单用例运行时限(毫秒)'' AFTER difficulty',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank' AND COLUMN_NAME = 'memory_limit_mb');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE question_bank ADD COLUMN memory_limit_mb INT NOT NULL DEFAULT 256 COMMENT ''运行内存限制(MB)'' AFTER time_limit_ms',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 编程题用例 ----------
CREATE TABLE IF NOT EXISTS question_test_cases (
    id              BIGINT   NOT NULL COMMENT '主键，雪花 ID',
    question_id     BIGINT   NOT NULL COMMENT 'question_bank.id',
    input           TEXT     NULL COMMENT '标准输入',
    expected_output TEXT     NOT NULL COMMENT '期望输出',
    is_sample       TINYINT  NOT NULL DEFAULT 0 COMMENT '1 例题（下发前端）/ 0 隐藏用例',
    sort            INT      NOT NULL DEFAULT 0 COMMENT '执行顺序',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_question_sample (question_id, is_sample, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编程题测试用例';

-- ---------- 代码提交 ----------
CREATE TABLE IF NOT EXISTS question_submissions (
    id            BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    user_id       BIGINT       NOT NULL COMMENT 'users.id',
    question_id   BIGINT       NOT NULL COMMENT 'question_bank.id',
    language      VARCHAR(16)  NOT NULL COMMENT 'JAVA / PYTHON',
    code          MEDIUMTEXT   NOT NULL COMMENT '提交代码',
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/ACCEPTED/WRONG_ANSWER/COMPILE_ERROR/RUNTIME_ERROR/TIME_LIMIT_EXCEEDED/SYSTEM_ERROR',
    passed        INT          NOT NULL DEFAULT 0 COMMENT '通过用例数',
    total         INT          NOT NULL DEFAULT 0 COMMENT '用例总数',
    runtime_ms    INT          NULL COMMENT '总运行耗时(毫秒)',
    error_message TEXT         NULL COMMENT '编译/运行错误或首个失败用例信息',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at   DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_user_question_created (user_id, question_id, created_at),
    KEY idx_question_created (question_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='编程题提交记录';
