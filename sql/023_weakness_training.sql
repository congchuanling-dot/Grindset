-- 023_weakness_training.sql
-- 弱项驱动复练闭环：面试弱项、知识点掌握度、训练计划
-- 手工执行：mysql -uroot -p123456 grindset < sql/023_weakness_training.sql

-- ---------- 面试弱项 ----------
CREATE TABLE IF NOT EXISTS interview_weakness (
    id          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    user_id     BIGINT       NOT NULL COMMENT 'users.id',
    session_id  BIGINT       NOT NULL COMMENT 'interview_sessions.id',
    question_id BIGINT       NULL COMMENT 'interview_questions.id，可为空',
    question_seq INT         NULL COMMENT '对应题号，便于报告回溯',
    kp_key      VARCHAR(80)  NOT NULL COMMENT '归一化知识点键',
    kp_name     VARCHAR(80)  NOT NULL COMMENT '知识点展示名',
    domain      VARCHAR(32)  NULL COMMENT '领域，如 Redis / MySQL',
    severity    TINYINT      NOT NULL DEFAULT 2 COMMENT '1 低 / 2 中 / 3 高',
    evidence    VARCHAR(500) NULL COMMENT '候选人原话或回答摘录',
    advice      VARCHAR(200) NULL COMMENT '建议练习形态',
    source      VARCHAR(16)  NOT NULL DEFAULT 'REPORT' COMMENT 'REPORT / QUESTION / JD',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_session (user_id, session_id),
    KEY idx_user_kp (user_id, kp_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试弱项诊断';

-- ---------- 用户知识点掌握度 ----------
CREATE TABLE IF NOT EXISTS user_kp_mastery (
    id                BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    user_id           BIGINT      NOT NULL COMMENT 'users.id',
    kp_key            VARCHAR(80) NOT NULL COMMENT '归一化知识点键',
    kp_name           VARCHAR(80) NOT NULL COMMENT '知识点展示名',
    domain            VARCHAR(32) NULL COMMENT '领域',
    mastery           TINYINT     NOT NULL DEFAULT 50 COMMENT '掌握度 0~100',
    level             VARCHAR(16) NOT NULL DEFAULT 'LEARNING' COMMENT 'WEAK / LEARNING / MASTERED',
    evidence_count    INT         NOT NULL DEFAULT 0 COMMENT '观测次数',
    last_score        TINYINT     NULL COMMENT '最近一次得分 1~5',
    last_source       VARCHAR(16) NULL COMMENT 'INTERVIEW / TRAINING',
    last_before       TINYINT     NULL COMMENT '最近一次变化前掌握度',
    last_delta        TINYINT     NULL COMMENT '最近一次变化量',
    last_practiced_at DATETIME    NULL COMMENT '最近练习时间',
    next_review_at    DATETIME    NULL COMMENT '下次建议复习时间',
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_kp (user_id, kp_key),
    KEY idx_user_level (user_id, level),
    KEY idx_user_review (user_id, next_review_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户知识点掌握度';

-- ---------- 掌握度变化日志 ----------
CREATE TABLE IF NOT EXISTS user_kp_mastery_log (
    id             BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    user_id        BIGINT      NOT NULL COMMENT 'users.id',
    kp_key         VARCHAR(80) NOT NULL COMMENT '归一化知识点键',
    before_score   TINYINT     NOT NULL COMMENT '变化前掌握度',
    after_score    TINYINT     NOT NULL COMMENT '变化后掌握度',
    delta          TINYINT     NOT NULL COMMENT '变化量',
    score          TINYINT     NULL COMMENT '本次作答得分 1~5',
    source_type    VARCHAR(16) NOT NULL COMMENT 'INTERVIEW / TRAINING',
    source_id      BIGINT      NULL COMMENT '场次或计划 ID',
    source_item_id BIGINT      NULL COMMENT '面试题 ID 或训练题 ID，用于去重',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_kp_time (user_id, kp_key, created_at),
    KEY idx_source_item (user_id, source_type, source_id, source_item_id, kp_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点掌握度变化日志';

-- ---------- 训练计划 ----------
CREATE TABLE IF NOT EXISTS training_plan (
    id              BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    user_id         BIGINT       NOT NULL COMMENT 'users.id',
    source_type     VARCHAR(16)  NOT NULL COMMENT 'INTERVIEW / MANUAL / DAILY',
    source_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '来源 ID，INTERVIEW 时为 session_id',
    source_key      VARCHAR(80)  NOT NULL COMMENT '幂等键：INTERVIEW:{sessionId} / MANUAL:{snowflake}',
    title           VARCHAR(120) NOT NULL COMMENT '计划标题',
    goal_json       JSON         NULL COMMENT '生成时的弱项与掌握度快照',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待开始 / 1 进行中 / 2 已完成 / 3 已过期',
    item_count      INT          NOT NULL DEFAULT 0 COMMENT '题目数',
    completed_count INT          NOT NULL DEFAULT 0 COMMENT '已完成数',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      DATETIME     NULL,
    finished_at     DATETIME     NULL,
    expires_at      DATETIME     NULL COMMENT '过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_source_key (user_id, source_key),
    KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弱项训练计划';

-- ---------- 训练题目 ----------
CREATE TABLE IF NOT EXISTS training_plan_item (
    id             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    plan_id        BIGINT       NOT NULL COMMENT 'training_plan.id',
    user_id        BIGINT       NOT NULL COMMENT 'users.id，便于按用户查询',
    seq            INT          NOT NULL COMMENT '题号 1~N',
    item_role      VARCHAR(16)  NOT NULL DEFAULT 'PRACTICE' COMMENT 'PRACTICE / RETEST',
    kp_key         VARCHAR(80)  NOT NULL COMMENT '归一化知识点键',
    kp_name        VARCHAR(80)  NOT NULL COMMENT '知识点展示名',
    item_type      VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT / CODING',
    question_id    BIGINT       NULL COMMENT '题库题目 ID，生成题为空',
    generated_json JSON         NULL COMMENT 'AI 生成题内容',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未做 / 1 已完成',
    score          TINYINT      NULL COMMENT '得分 1~5',
    attempt_id     BIGINT       NULL COMMENT 'question_attempts.id，生成题为空',
    answer_text    TEXT         NULL COMMENT '本次作答原文',
    hit_points     JSON         NULL COMMENT '命中要点',
    missed_points  JSON         NULL COMMENT '遗漏要点',
    comment        VARCHAR(500) NULL COMMENT '点评',
    suggestion     VARCHAR(500) NULL COMMENT '建议',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at    DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_seq (plan_id, seq),
    KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弱项训练题目';
