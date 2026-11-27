-- ============================================================
-- 面试 JD / 公司 / 级别定制
--   interview_sessions 保存目标公司与 JD 原文、解析后的岗位画像
--   interview_questions 记录本题考察的 JD 能力项
-- 脚本可重复执行
-- ============================================================

USE grindset;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @ddl_sql = ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('interview_sessions', 'company',
    'ALTER TABLE interview_sessions ADD COLUMN company VARCHAR(64) NULL COMMENT ''目标公司'' AFTER position');

CALL add_column_if_missing('interview_sessions', 'job_level',
    'ALTER TABLE interview_sessions ADD COLUMN job_level VARCHAR(32) NULL COMMENT ''intern/campus/experienced/senior'' AFTER company');

CALL add_column_if_missing('interview_sessions', 'jd_text',
    'ALTER TABLE interview_sessions ADD COLUMN jd_text MEDIUMTEXT NULL COMMENT ''JD 原文,最多 8000 字'' AFTER job_level');

CALL add_column_if_missing('interview_sessions', 'jd_profile_json',
    'ALTER TABLE interview_sessions ADD COLUMN jd_profile_json JSON NULL COMMENT ''JD 解析后的岗位画像'' AFTER jd_text');

CALL add_column_if_missing('interview_questions', 'competency',
    'ALTER TABLE interview_questions ADD COLUMN competency VARCHAR(64) NULL COMMENT ''本题考察的 JD 能力项'' AFTER knowledge_points');

DROP PROCEDURE IF EXISTS add_column_if_missing;
