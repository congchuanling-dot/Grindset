-- ============================================================
-- 简历原件存储扩展
-- resumes 增加 original_object_key:阿里云 OSS 私有桶对象 key
-- 未配置 OSS 或上传失败时该列为空,不影响解析结果
-- 脚本可重复执行
-- ============================================================

USE grindset;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resumes' AND COLUMN_NAME = 'original_object_key');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE resumes ADD COLUMN original_object_key VARCHAR(512) NULL COMMENT ''阿里云 OSS 对象 key，私有桶，仅后端签名访问'' AFTER file_size',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
