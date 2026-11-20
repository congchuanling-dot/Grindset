-- ============================================================
-- 首页 Agent 会话持久化建表脚本
-- 一条会话 = 多条消息,按 seq 升序还原完整对话(含工具调用)
-- 主键为雪花算法生成的 BIGINT,由 Java 代码写入(非数据库自增)
-- ============================================================

USE grindset;

-- ---------- Agent 会话:一个聊天窗口一条记录 ----------
CREATE TABLE IF NOT EXISTS agent_conversations (
    id              BIGINT       NOT NULL COMMENT '主键,雪花 ID(由应用生成)',
    user_id         BIGINT       NOT NULL COMMENT '所属用户 users.id',
    title           VARCHAR(64)  NOT NULL COMMENT '会话标题,默认截断首条提问,首轮结束后用 LLM 生成',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息条数,同时作为下一次写入的 seq 起点',
    last_message_at DATETIME     NULL COMMENT '最后一条消息时间,列表按它倒序',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_last (user_id, last_message_at) COMMENT '按用户查会话列表,按最后消息时间倒序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页 Agent 会话';

-- ---------- Agent 消息:完整消息流,可重放给模型,也可渲染历史 ----------
CREATE TABLE IF NOT EXISTS agent_messages (
    id              BIGINT       NOT NULL COMMENT '主键,雪花 ID',
    conversation_id BIGINT       NOT NULL COMMENT 'agent_conversations.id',
    seq             INT          NOT NULL COMMENT '会话内顺序,从 1 开始',
    role            VARCHAR(16)  NOT NULL COMMENT 'USER 用户 / ASSISTANT 助手 / TOOL 工具结果',
    content         TEXT         NULL COMMENT '用户提问、助手回答或工具结果 JSON',
    tool_calls_json JSON         NULL COMMENT 'assistant 发起工具调用时: [{id,name,arguments}]',
    tool_call_id    VARCHAR(64)  NULL COMMENT 'TOOL 消息对应的 tool_call_id',
    tool_meta_json  JSON         NULL COMMENT 'TOOL 消息给前端展示用: {name,label,args,ok,summary}',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '仅 assistant: 0 正常 / 2 失败 / 3 中断',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conv_seq (conversation_id, seq) COMMENT '同一会话内 seq 唯一,防重复写入',
    KEY idx_conv_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页 Agent 消息(完整流,可重放)';
