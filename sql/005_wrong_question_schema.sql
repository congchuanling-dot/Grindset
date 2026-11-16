-- ============================================================
-- 错题重练闭环建表脚本
-- 设计说明见《错题重练闭环-技术设计》
-- 主键为雪花算法生成的 BIGINT,由 Java 代码写入(非数据库自增)
-- wrong_questions 是错题快照,question_retries 是每次重练记录
-- ============================================================

USE grindset;

-- ---------- 错题快照:一题一条,独立于原面试 ----------
CREATE TABLE IF NOT EXISTS wrong_questions (
    id                  BIGINT        NOT NULL COMMENT '主键,雪花 ID',
    user_id             BIGINT        NOT NULL COMMENT 'users.id',
    session_id          BIGINT        NULL COMMENT '来源面试会话 interview_sessions.id',
    question_id         BIGINT        NULL COMMENT '来源题目 interview_questions.id',
    position            VARCHAR(64)   NULL COMMENT '当时的面试岗位',
    `round`             VARCHAR(32)   NULL COMMENT '当时的面试轮次',
    question_content    TEXT          NOT NULL COMMENT '主问题题干快照',
    question_context    TEXT          NULL COMMENT '追问问题拼接',
    original_answer     TEXT          NULL COMMENT '原回答,所有 ANSWER 按顺序拼接',
    original_score      TINYINT       NULL COMMENT '原评分 1~5',
    original_comment    VARCHAR(500)  NULL COMMENT '原总评',
    original_suggestion VARCHAR(500)  NULL COMMENT '原改进建议',
    original_feedback   VARCHAR(1000) NULL COMMENT '逐轮一句话点评拼接',
    knowledge_points    JSON          NULL COMMENT '知识点名称快照',
    kp_ids              JSON          NULL COMMENT '知识点 ID 快照',
    status              TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待重练 / 1 已掌握',
    retry_count         INT           NOT NULL DEFAULT 0 COMMENT '重练次数',
    best_score          TINYINT       NULL COMMENT '重练最高分',
    last_score          TINYINT       NULL COMMENT '最近一次重练得分',
    last_retry_at       DATETIME      NULL COMMENT '最近重练时间',
    next_review_at      DATETIME      NULL COMMENT '下次建议复习时间',
    mastered_at         DATETIME      NULL COMMENT '最近一次标记已掌握的时间',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_question (user_id, question_id) COMMENT '同一题只收录一次',
    KEY idx_user_status_review (user_id, status, next_review_at) COMMENT '错题本列表',
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题快照';

-- ---------- 重练记录:每次提交一条,不覆盖历史 ----------
CREATE TABLE IF NOT EXISTS question_retries (
    id               BIGINT       NOT NULL COMMENT '主键,雪花 ID',
    wrong_id         BIGINT       NOT NULL COMMENT 'wrong_questions.id',
    user_id          BIGINT       NOT NULL COMMENT 'users.id',
    answer           TEXT         NOT NULL COMMENT '本次重练回答',
    score            TINYINT      NOT NULL COMMENT '本次评分 1~5',
    comment          VARCHAR(500) NULL COMMENT '本次点评',
    suggestion       VARCHAR(500) NULL COMMENT '本次建议',
    reference_answer TEXT         NULL COMMENT '本次生成的参考要点',
    knowledge_points JSON         NULL COMMENT '本次回写画像使用的知识点快照',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_wrong_created (wrong_id, created_at) COMMENT '按错题查重练历史',
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题重练记录';
