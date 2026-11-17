-- ============================================================
-- 题库第一版建表脚本（力扣刷题形态）
-- 主键为雪花算法生成的 BIGINT，由 Java 代码写入（非数据库自增）
-- question_bank           题库内容（人工精选种子题 + 后续导入）
-- user_question_progress  每个用户每道题的做题进度（0 未做 / 1 已做 / 2 错题）
-- question_attempts       每次作答记录（含 AI 命中/遗漏要点）
-- wrong_questions         增加 source 列：INTERVIEW 面试 / BANK 题库
-- ============================================================

USE grindset;

-- ---------- 题库内容 ----------
CREATE TABLE IF NOT EXISTS question_bank (
    id               BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    title            VARCHAR(200) NOT NULL COMMENT '题目标题，列表展示用',
    content          TEXT         NOT NULL COMMENT '题干',
    type             VARCHAR(16)  NOT NULL DEFAULT 'BASICS' COMMENT 'BASICS 八股 / SCENARIO 场景 / PROJECT 项目',
    difficulty       TINYINT      NOT NULL DEFAULT 2 COMMENT '难度 1~3',
    domain           VARCHAR(32)  NOT NULL DEFAULT '其他' COMMENT '领域，如 Redis / MySQL / JVM / Java / 网络 / 操作系统 / 项目',
    reference_answer TEXT         NULL COMMENT '参考答案',
    key_points       JSON         NULL COMMENT '关键要点数组',
    kp_ids           JSON         NULL COMMENT '知识点 ID 快照',
    kp_names         JSON         NULL COMMENT '知识点名称快照',
    source           VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 人工 / AI / PDF / CRAWL',
    source_url       VARCHAR(512) NULL COMMENT '来源链接（导入场景）',
    content_hash     CHAR(64)     NOT NULL COMMENT '题干内容 SHA-256，用于导入幂等',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 / 0 下架',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_hash (content_hash),
    KEY idx_domain_difficulty (domain, difficulty),
    KEY idx_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

-- ---------- 用户做题进度 ----------
CREATE TABLE IF NOT EXISTS user_question_progress (
    id              BIGINT   NOT NULL COMMENT '主键，雪花 ID',
    user_id         BIGINT   NOT NULL COMMENT 'users.id',
    question_id     BIGINT   NOT NULL COMMENT 'question_bank.id',
    status          TINYINT  NOT NULL DEFAULT 1 COMMENT '1 已做 / 2 错题',
    attempt_count   INT      NOT NULL DEFAULT 0 COMMENT '作答次数',
    best_score      TINYINT  NULL COMMENT '历史最高分',
    last_score      TINYINT  NULL COMMENT '最近一次得分',
    last_attempt_at DATETIME NULL COMMENT '最近作答时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_question (user_id, question_id),
    KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户题库做题进度';

-- ---------- 每次作答记录 ----------
CREATE TABLE IF NOT EXISTS question_attempts (
    id            BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    user_id       BIGINT       NOT NULL COMMENT 'users.id',
    question_id   BIGINT       NOT NULL COMMENT 'question_bank.id',
    answer        TEXT         NOT NULL COMMENT '本次回答',
    score         TINYINT      NOT NULL COMMENT 'AI 评分 1~5',
    hit_points    JSON         NULL COMMENT '命中的要点',
    missed_points JSON         NULL COMMENT '遗漏的要点',
    comment       VARCHAR(500) NULL COMMENT '点评',
    suggestion    VARCHAR(500) NULL COMMENT '改进建议',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_question_created (user_id, question_id, created_at),
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库作答记录';

-- ---------- 错题快照增加来源列（可重复执行） ----------
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wrong_questions'
      AND COLUMN_NAME = 'source'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE wrong_questions ADD COLUMN `source` VARCHAR(16) NOT NULL DEFAULT ''INTERVIEW'' COMMENT ''来源：INTERVIEW 面试 / BANK 题库'' AFTER question_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
