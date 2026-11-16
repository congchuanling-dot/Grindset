-- ============================================================
-- AI 面试建表脚本
-- 设计说明见对话中的《AI 面试小闭环-技术方案》
-- 主键为雪花算法生成的 BIGINT,由 Java 代码写入(非数据库自增)
-- 一道"大题" = 主问题 + 1~3 次追问,线程内消息落在 interview_messages
-- ============================================================

USE grindset;

-- ---------- 面试会话:一次面试一条记录 ----------
CREATE TABLE IF NOT EXISTS interview_sessions (
    id                   BIGINT       NOT NULL COMMENT '主键,雪花 ID(由应用生成)',
    user_id              BIGINT       NOT NULL COMMENT '所属用户 users.id',
    resume_id            BIGINT       NOT NULL COMMENT '使用的简历 resumes.id',
    position             VARCHAR(64)  NOT NULL COMMENT '面试岗位',
    style                VARCHAR(16)  NOT NULL DEFAULT 'middle' COMMENT 'pressure 压力面 / middle 适中 / relaxed 轻松',
    round                VARCHAR(32)  NULL COMMENT '面试轮次,如 一面/二面/HR 面',
    focus                JSON         NULL COMMENT '侧重方向 ["项目深挖","基础八股"]',
    question_count       INT          NOT NULL DEFAULT 5 COMMENT '计划题目数 3/5/8',
    completed_questions  INT          NOT NULL DEFAULT 0 COMMENT '已收束的题目数',
    status               TINYINT      NOT NULL DEFAULT 0 COMMENT '0 进行中 / 1 已结束',
    overall_score        INT          NULL COMMENT '复盘总分 0~100',
    report_json          JSON         NULL COMMENT '复盘报告完整 JSON',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at) COMMENT '按用户查历史会话,倒序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 面试会话';

-- ---------- 面试题目线程:一道大题一行 ----------
CREATE TABLE IF NOT EXISTS interview_questions (
    id                BIGINT       NOT NULL COMMENT '主键,雪花 ID',
    session_id        BIGINT       NOT NULL COMMENT 'interview_sessions.id',
    seq               INT          NOT NULL COMMENT '第几题,从 1 开始',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未开始 / 1 提问中 / 2 作答中 / 3 已收束',
    follow_up_count   INT          NOT NULL DEFAULT 0 COMMENT '已追问次数,0~3',
    score             TINYINT      NULL COMMENT '本题评分 1~5',
    knowledge_points  JSON         NULL COMMENT '本题命中知识点',
    comment           VARCHAR(500) NULL COMMENT '本题总评',
    suggestion        VARCHAR(500) NULL COMMENT '改进建议',
    evaluation_json   JSON         NULL COMMENT '完整点评 JSON,便于后续扩展',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at       DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_seq (session_id, seq) COMMENT '同一会话题号唯一,防重复'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 面试题目线程';

-- ---------- 线程内消息:主问题/追问/回答/一句话点评 ----------
CREATE TABLE IF NOT EXISTS interview_messages (
    id           BIGINT      NOT NULL COMMENT '主键,雪花 ID',
    question_id  BIGINT      NOT NULL COMMENT 'interview_questions.id',
    seq          INT         NOT NULL COMMENT '线程内顺序,从 1 开始',
    role         VARCHAR(16) NOT NULL COMMENT 'INTERVIEWER 面试官 / USER 候选人',
    type         VARCHAR(16) NOT NULL COMMENT 'QUESTION 主问题 / FOLLOW_UP 追问 / ANSWER 回答 / FEEDBACK 点评',
    content      TEXT        NOT NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_question_seq (question_id, seq) COMMENT '按题目查线程消息,按顺序渲染'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 面试线程消息';
