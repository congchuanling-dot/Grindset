-- 024_training_v2.sql
-- 弱项训练 v2：能力维度、训练阶段、错题本、维度分、成长快照
-- 手工执行：mysql --default-character-set=utf8mb4 -uroot -p123456 grindset < sql/024_training_v2.sql

-- ---------- 为已有表补充列（可重复执行） ----------
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE interview_weakness ADD COLUMN dimension VARCHAR(24) NULL AFTER domain',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_weakness' AND COLUMN_NAME = 'dimension');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE user_kp_mastery ADD COLUMN dimension VARCHAR(24) NULL AFTER domain',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_kp_mastery' AND COLUMN_NAME = 'dimension');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE user_kp_mastery ADD COLUMN consecutive_good INT NOT NULL DEFAULT 0 AFTER last_delta',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_kp_mastery' AND COLUMN_NAME = 'consecutive_good');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE user_kp_mastery ADD COLUMN eliminated_at DATETIME NULL AFTER next_review_at',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_kp_mastery' AND COLUMN_NAME = 'eliminated_at');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE user_kp_mastery_log ADD COLUMN dimension VARCHAR(24) NULL AFTER kp_key',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_kp_mastery_log' AND COLUMN_NAME = 'dimension');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan ADD COLUMN stage VARCHAR(16) NOT NULL DEFAULT ''DAILY'' AFTER source_id',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan' AND COLUMN_NAME = 'stage');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan ADD COLUMN dimension_focus VARCHAR(24) NULL AFTER stage',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan' AND COLUMN_NAME = 'dimension_focus');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan ADD COLUMN policy_json JSON NULL AFTER goal_json',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan' AND COLUMN_NAME = 'policy_json');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan ADD COLUMN accuracy TINYINT NULL AFTER completed_count',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan' AND COLUMN_NAME = 'accuracy');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan ADD COLUMN avg_score TINYINT NULL AFTER accuracy',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan' AND COLUMN_NAME = 'avg_score');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan_item ADD COLUMN dimension VARCHAR(24) NULL AFTER kp_name',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan_item' AND COLUMN_NAME = 'dimension');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan_item ADD COLUMN difficulty TINYINT NULL AFTER item_type',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan_item' AND COLUMN_NAME = 'difficulty');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan_item ADD COLUMN feedback_json JSON NULL AFTER suggestion',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan_item' AND COLUMN_NAME = 'feedback_json');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan_item ADD COLUMN similar_json JSON NULL AFTER feedback_json',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan_item' AND COLUMN_NAME = 'similar_json');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE training_plan_item ADD COLUMN mistake_id BIGINT NULL AFTER attempt_id',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_plan_item' AND COLUMN_NAME = 'mistake_id');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 错题本 ----------
CREATE TABLE IF NOT EXISTS training_mistake (
    id                BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    user_id           BIGINT       NOT NULL COMMENT 'users.id',
    plan_id           BIGINT       NULL COMMENT 'training_plan.id',
    plan_item_id      BIGINT       NULL COMMENT 'training_plan_item.id',
    kp_key            VARCHAR(80)  NOT NULL COMMENT '归一化知识点键',
    kp_name           VARCHAR(80)  NOT NULL COMMENT '知识点展示名',
    dimension         VARCHAR(24)  NULL COMMENT '能力维度',
    question_id       BIGINT       NULL COMMENT '题库题 ID，生成题为空',
    question_snapshot JSON         NULL COMMENT '题目快照，保证错题长期可重练',
    answer_text       TEXT         NULL COMMENT '原作答',
    score             TINYINT      NOT NULL COMMENT '本次得分 1~5',
    missed_points     JSON         NULL COMMENT '遗漏要点',
    comment           VARCHAR(500) NULL COMMENT '点评',
    suggestion        VARCHAR(500) NULL COMMENT '建议',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未解决 / 1 已解决',
    review_count      INT          NOT NULL DEFAULT 0 COMMENT '重练次数',
    next_review_at    DATETIME     NULL COMMENT '下次复习时间',
    resolved_at       DATETIME     NULL COMMENT '解决时间',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_user_kp (user_id, kp_key),
    KEY idx_user_review (user_id, next_review_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弱项训练错题本';

-- ---------- 五维当前分 ----------
CREATE TABLE IF NOT EXISTS user_dimension_mastery (
    id         BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    user_id    BIGINT      NOT NULL COMMENT 'users.id',
    dimension  VARCHAR(24) NOT NULL COMMENT 'BASICS/PROJECT/SYSTEM_DESIGN/COMMUNICATION/CODING',
    mastery    TINYINT     NOT NULL DEFAULT 50 COMMENT '0~100',
    source     VARCHAR(16) NULL COMMENT 'INTERVIEW / TRAINING',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_dimension (user_id, dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户五维能力分';

-- ---------- 每日成长快照 ----------
CREATE TABLE IF NOT EXISTS training_mastery_snapshot (
    id                BIGINT   NOT NULL COMMENT '主键，雪花 ID',
    user_id           BIGINT   NOT NULL COMMENT 'users.id',
    snapshot_date     DATE     NOT NULL COMMENT '快照日期',
    overall_mastery   TINYINT  NOT NULL COMMENT '总体 readiness',
    dimension_json    JSON     NULL COMMENT '当日五维分',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_date (user_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练成长每日快照';
