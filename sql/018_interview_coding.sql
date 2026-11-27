-- ============================================================
-- AI 面试手撕代码环节
--   interview_sessions    最后一题是否手撕代码 + 编程时限
--   interview_questions   编程题类型、题库关联、判题结果、代码草稿
--   question_submissions  区分题库提交(BANK)与面试提交(INTERVIEW)
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

DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_index_if_missing(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl_sql = ddl;
        PREPARE stmt FROM @ddl_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------- 面试会话 ----------
CALL add_column_if_missing('interview_sessions', 'coding_enabled',
    'ALTER TABLE interview_sessions ADD COLUMN coding_enabled TINYINT NOT NULL DEFAULT 0 COMMENT ''1 最后一题手撕代码'' AFTER question_count');
CALL add_column_if_missing('interview_sessions', 'coding_time_limit_sec',
    'ALTER TABLE interview_sessions ADD COLUMN coding_time_limit_sec INT NOT NULL DEFAULT 1800 COMMENT ''编程环节时限(秒)'' AFTER coding_enabled');

-- ---------- 面试题目线程 ----------
CALL add_column_if_missing('interview_questions', 'question_type',
    'ALTER TABLE interview_questions ADD COLUMN question_type VARCHAR(16) NOT NULL DEFAULT ''VERBAL'' COMMENT ''VERBAL 语音题 / CODING 编程题'' AFTER seq');
CALL add_column_if_missing('interview_questions', 'coding_question_id',
    'ALTER TABLE interview_questions ADD COLUMN coding_question_id BIGINT NULL COMMENT ''question_bank.id'' AFTER question_type');
CALL add_column_if_missing('interview_questions', 'coding_title',
    'ALTER TABLE interview_questions ADD COLUMN coding_title VARCHAR(200) NULL COMMENT ''编程题标题快照'' AFTER coding_question_id');
CALL add_column_if_missing('interview_questions', 'coding_mode',
    'ALTER TABLE interview_questions ADD COLUMN coding_mode VARCHAR(16) NULL COMMENT ''ACM / LEETCODE'' AFTER coding_title');
CALL add_column_if_missing('interview_questions', 'coding_submission_id',
    'ALTER TABLE interview_questions ADD COLUMN coding_submission_id BIGINT NULL COMMENT ''最近一次提交'' AFTER coding_mode');
CALL add_column_if_missing('interview_questions', 'judge_status',
    'ALTER TABLE interview_questions ADD COLUMN judge_status VARCHAR(32) NULL COMMENT ''判题终态'' AFTER coding_submission_id');
CALL add_column_if_missing('interview_questions', 'passed',
    'ALTER TABLE interview_questions ADD COLUMN passed INT NULL COMMENT ''通过用例数'' AFTER judge_status');
CALL add_column_if_missing('interview_questions', 'total',
    'ALTER TABLE interview_questions ADD COLUMN total INT NULL COMMENT ''用例总数'' AFTER passed');
CALL add_column_if_missing('interview_questions', 'code_draft',
    'ALTER TABLE interview_questions ADD COLUMN code_draft MEDIUMTEXT NULL COMMENT ''最近一次代码草稿'' AFTER total');
CALL add_column_if_missing('interview_questions', 'code_language',
    'ALTER TABLE interview_questions ADD COLUMN code_language VARCHAR(16) NULL COMMENT ''JAVA / PYTHON'' AFTER code_draft');
CALL add_column_if_missing('interview_questions', 'started_at',
    'ALTER TABLE interview_questions ADD COLUMN started_at DATETIME NULL COMMENT ''编程开始时间'' AFTER created_at');
CALL add_column_if_missing('interview_questions', 'deadline_at',
    'ALTER TABLE interview_questions ADD COLUMN deadline_at DATETIME NULL COMMENT ''编程截止时间'' AFTER started_at');
CALL add_index_if_missing('interview_questions', 'idx_session_type',
    'ALTER TABLE interview_questions ADD INDEX idx_session_type (session_id, question_type)');

-- ---------- 提交记录:区分题库与面试 ----------
CALL add_column_if_missing('question_submissions', 'scene',
    'ALTER TABLE question_submissions ADD COLUMN scene VARCHAR(16) NOT NULL DEFAULT ''BANK'' COMMENT ''BANK 题库 / INTERVIEW 面试'' AFTER question_id');
CALL add_column_if_missing('question_submissions', 'interview_session_id',
    'ALTER TABLE question_submissions ADD COLUMN interview_session_id BIGINT NULL COMMENT ''面试会话 id'' AFTER scene');
CALL add_column_if_missing('question_submissions', 'interview_question_id',
    'ALTER TABLE question_submissions ADD COLUMN interview_question_id BIGINT NULL COMMENT ''面试题目 id'' AFTER interview_session_id');
CALL add_index_if_missing('question_submissions', 'idx_scene_user',
    'ALTER TABLE question_submissions ADD INDEX idx_scene_user (scene, user_id, created_at)');
CALL add_index_if_missing('question_submissions', 'idx_interview_session',
    'ALTER TABLE question_submissions ADD INDEX idx_interview_session (interview_session_id, created_at)');
CALL add_index_if_missing('question_submissions', 'idx_interview_question',
    'ALTER TABLE question_submissions ADD INDEX idx_interview_question (interview_question_id, created_at)');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
