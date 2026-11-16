-- ============================================================
-- 掌握度画像建表脚本
-- 设计说明见《P0-掌握度画像与加权出题-技术设计》
-- 主键为雪花算法生成的 BIGINT,由 Java 代码写入(非数据库自增)
-- knowledge_points 是全局知识点字典,user_knowledge_states 是用户掌握度
-- ============================================================

USE grindset;

-- ---------- 知识点字典:归一后的名称全局唯一 ----------
CREATE TABLE IF NOT EXISTS knowledge_points (
    id               BIGINT       NOT NULL COMMENT '主键,雪花 ID(由应用生成)',
    name             VARCHAR(128) NOT NULL COMMENT '规范名称,展示用',
    normalized_name  VARCHAR(128) NOT NULL COMMENT '归一化名称,去空白/标点/统一小写',
    domain           VARCHAR(32)  NOT NULL DEFAULT '其他' COMMENT '领域,用于画像筛选',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_normalized (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点字典';

-- ---------- 用户掌握度:一个用户对一个知识点一条 ----------
CREATE TABLE IF NOT EXISTS user_knowledge_states (
    id                BIGINT   NOT NULL COMMENT '主键,雪花 ID',
    user_id           BIGINT   NOT NULL COMMENT 'users.id',
    kp_id             BIGINT   NOT NULL COMMENT 'knowledge_points.id',
    mastery           INT      NOT NULL DEFAULT 0 COMMENT '掌握度 0~100',
    prev_mastery      INT      NULL COMMENT '上一次掌握度,用于趋势',
    practice_count    INT      NOT NULL DEFAULT 0 COMMENT '被考察次数',
    correct_count     INT      NOT NULL DEFAULT 0 COMMENT '得分>=3 的次数',
    last_score        TINYINT  NULL COMMENT '最近一次评分 1~5',
    last_session_id   BIGINT   NULL COMMENT '最近一次来源会话',
    last_practiced_at DATETIME NULL COMMENT '最近练习时间',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_kp (user_id, kp_id) COMMENT '同一用户同一知识点只有一条',
    KEY idx_user_mastery (user_id, mastery) COMMENT '画像页按掌握度排序',
    KEY idx_user_practiced (user_id, last_practiced_at) COMMENT '算遗忘权重、取最近知识点'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户知识点掌握度';
