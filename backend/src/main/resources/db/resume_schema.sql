-- ============================================================
-- 简历解析建表脚本
-- 设计说明见 docs/简历解析-技术设计.md
-- 主键为雪花算法生成的 BIGINT,由 Java 代码写入(非数据库自增)
-- 不保存简历原件与原始纯文本,只保存结构化结果
-- ============================================================

USE grindset;

-- ---------- 简历主表:一次上传一条记录 ----------
CREATE TABLE IF NOT EXISTS resumes (
    id                BIGINT        NOT NULL COMMENT '主键,雪花 ID(由应用生成)',
    user_id           BIGINT        NOT NULL COMMENT '所属用户 users.id',
    file_name         VARCHAR(255)  NOT NULL COMMENT '上传文件名',
    file_type         VARCHAR(16)   NOT NULL COMMENT 'PDF/DOCX/MD/TXT',
    file_size         INT           NOT NULL COMMENT '文件字节数',
    candidate_name    VARCHAR(128)  NULL COMMENT '候选人姓名',
    candidate_type    VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'FRESH_GRADUATE 应届 / INTERN 找实习 / EXPERIENCED 社招 / UNKNOWN',
    parse_status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0 解析中 / 1 成功 / 2 失败',
    error_msg         VARCHAR(500)  NULL COMMENT '失败原因,给用户看',
    model             VARCHAR(64)   NULL COMMENT '解析使用的模型 ID',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at) COMMENT '按用户查简历列表,倒序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历主表';

-- ---------- 简历章节:任意章节都能落表 ----------
CREATE TABLE IF NOT EXISTS resume_sections (
    id            BIGINT       NOT NULL COMMENT '主键,雪花 ID',
    resume_id     BIGINT       NOT NULL COMMENT 'resumes.id',
    section_type  VARCHAR(32)  NOT NULL COMMENT '章节类型:EDUCATION/WORK/INTERNSHIP/PROJECT/RESEARCH/AWARD/CAMPUS/CERTIFICATE/SUMMARY/OTHER',
    title         VARCHAR(128) NOT NULL COMMENT '原简历章节标题,如"科研经历"',
    sort          INT          NOT NULL DEFAULT 0 COMMENT '章节顺序',
    PRIMARY KEY (id),
    KEY idx_resume_sort (resume_id, sort) COMMENT '按简历查章节,按顺序渲染'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历章节';

-- ---------- 章节条目:公司/学校/项目/论文/奖项 ----------
CREATE TABLE IF NOT EXISTS resume_section_items (
    id           BIGINT       NOT NULL COMMENT '主键,雪花 ID',
    section_id   BIGINT       NOT NULL COMMENT 'resume_sections.id',
    title        VARCHAR(255) NULL COMMENT '主体:公司/学校/项目/论文/奖项名',
    subtitle     VARCHAR(255) NULL COMMENT '角色/专业/级别',
    start_date   VARCHAR(32)  NULL COMMENT '开始时间,保留原文,如 2023.06',
    end_date     VARCHAR(32)  NULL COMMENT '结束时间,保留原文,如 至今',
    description  TEXT         NULL COMMENT '描述正文',
    tags         JSON         NULL COMMENT '技术栈/关键词标签',
    sort         INT          NOT NULL DEFAULT 0 COMMENT '条目顺序',
    PRIMARY KEY (id),
    KEY idx_section_sort (section_id, sort) COMMENT '按章节查条目'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历章节条目';

-- ---------- 技能清单:单独成表便于查询和出题 ----------
CREATE TABLE IF NOT EXISTS resume_skills (
    id           BIGINT       NOT NULL COMMENT '主键,雪花 ID',
    resume_id    BIGINT       NOT NULL COMMENT 'resumes.id',
    name         VARCHAR(64)  NOT NULL COMMENT '技能名,如 Redis',
    category     VARCHAR(32)  NULL COMMENT '分类,如 缓存/数据库/语言',
    proficiency  TINYINT      NULL COMMENT '熟练度 1~5,LLM 推断',
    evidence     VARCHAR(500) NULL COMMENT '推断依据,引用简历原文',
    sort         INT          NOT NULL DEFAULT 0 COMMENT '展示顺序',
    PRIMARY KEY (id),
    KEY idx_resume_sort (resume_id, sort) COMMENT '按简历查技能'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历技能清单';
