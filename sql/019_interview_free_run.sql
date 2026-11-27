-- ============================================================
-- 面试手撕代码切换为 FREE_RUN 开放式运行模式
--   不比对测试用例,只执行候选人完整代码并保存控制台输出
--   interview_questions 增加最近一次运行结果
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

CALL add_column_if_missing('interview_questions', 'run_stdout',
    'ALTER TABLE interview_questions ADD COLUMN run_stdout MEDIUMTEXT NULL COMMENT ''最近一次运行标准输出'' AFTER code_language');
CALL add_column_if_missing('interview_questions', 'run_stderr',
    'ALTER TABLE interview_questions ADD COLUMN run_stderr MEDIUMTEXT NULL COMMENT ''最近一次运行错误输出'' AFTER run_stdout');
CALL add_column_if_missing('interview_questions', 'run_exit_code',
    'ALTER TABLE interview_questions ADD COLUMN run_exit_code INT NULL COMMENT ''最近一次运行退出码'' AFTER run_stderr');
CALL add_column_if_missing('interview_questions', 'run_runtime_ms',
    'ALTER TABLE interview_questions ADD COLUMN run_runtime_ms INT NULL COMMENT ''最近一次运行耗时(毫秒)'' AFTER run_exit_code');
CALL add_column_if_missing('interview_questions', 'run_at',
    'ALTER TABLE interview_questions ADD COLUMN run_at DATETIME NULL COMMENT ''最近一次运行时间'' AFTER run_runtime_ms');
CALL add_column_if_missing('interview_questions', 'run_count',
    'ALTER TABLE interview_questions ADD COLUMN run_count INT NOT NULL DEFAULT 0 COMMENT ''运行次数'' AFTER run_at');

DROP PROCEDURE IF EXISTS add_column_if_missing;
