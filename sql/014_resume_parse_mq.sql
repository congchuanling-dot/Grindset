-- ============================================================
-- 简历解析异步化扩展
-- resumes 增加解析尝试次数与开始/结束时间,供 RabbitMQ worker 与补偿任务使用
-- 脚本可重复执行
-- ============================================================

USE grindset;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resumes' AND COLUMN_NAME = 'parse_attempt');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE resumes ADD COLUMN parse_attempt TINYINT NOT NULL DEFAULT 0 COMMENT ''已尝试解析次数'' AFTER parse_status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resumes' AND COLUMN_NAME = 'parse_started_at');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE resumes ADD COLUMN parse_started_at DATETIME NULL COMMENT ''本次解析开始时间'' AFTER parse_attempt',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resumes' AND COLUMN_NAME = 'parse_finished_at');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE resumes ADD COLUMN parse_finished_at DATETIME NULL COMMENT ''解析结束时间'' AFTER parse_started_at',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
