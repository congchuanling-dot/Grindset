-- 大模型渠道 / 模型目录
-- 用途:前端模型下拉框和对话校验都读这两张表,改数据即时生效(服务端有短缓存)。
-- 执行:mysql --default-character-set=utf8mb4 -uroot -p123456 grindset < llm_catalog.sql
-- 注意必须带 --default-character-set=utf8mb4,否则中文展示名会被二次编码成乱码。
-- 脚本可重复执行:建表用 IF NOT EXISTS,种子数据用 INSERT IGNORE(不会覆盖你改过的值)。

CREATE TABLE IF NOT EXISTS `llm_channels` (
  `id` bigint NOT NULL COMMENT '主键,雪花 ID(种子数据用小整数,不会与雪花碰撞)',
  `provider_id` varchar(32) NOT NULL COMMENT '对应 application.yml 里 api-keys 的 key,如 deepseek',
  `name` varchar(64) NOT NULL COMMENT '展示名,如 DeepSeek / 通义千问',
  `provider_type` varchar(32) NOT NULL DEFAULT 'OPENAI_COMPAT' COMMENT '协议类型,决定用哪个 Client 实现',
  `base_url` varchar(255) NOT NULL COMMENT 'OpenAI 兼容根地址,不含 /chat/completions',
  `priority` int NOT NULL DEFAULT 0 COMMENT '同一 provider 多渠道时,数字大的优先',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '0 停用 / 1 启用',
  `balance` decimal(14,4) DEFAULT NULL COMMENT '账户余额,未知为 NULL',
  `balance_currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '币种,如 CNY/USD',
  `balance_source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 手工维护 / AUTO 定时查询',
  `balance_updated_at` datetime DEFAULT NULL COMMENT '余额最后更新时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_provider` (`provider_id`,`base_url`) COMMENT '同一 provider 可建多条渠道(多账号)',
  KEY `idx_enabled_priority` (`enabled`,`priority`) COMMENT '按启用状态 + 优先级取渠道'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型渠道(厂商 + 地址)';

CREATE TABLE IF NOT EXISTS `llm_models` (
  `id` bigint NOT NULL COMMENT '主键,雪花 ID(种子数据用小整数)',
  `channel_id` bigint NOT NULL COMMENT '关联 llm_channels.id(无物理外键,靠代码保证)',
  `model_id` varchar(128) NOT NULL COMMENT '调用厂商时传的模型 ID,如 deepseek-chat',
  `display_name` varchar(64) NOT NULL COMMENT '下拉框展示名',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '0 下架 / 1 上架',
  `sort` int NOT NULL DEFAULT 0 COMMENT '同渠道内排序,小的在前',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_model` (`channel_id`,`model_id`),
  KEY `idx_channel_enabled_sort` (`channel_id`,`enabled`,`sort`) COMMENT '按渠道取上架模型'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型模型目录';

CREATE TABLE IF NOT EXISTS `llm_keys` (
  `id` bigint NOT NULL COMMENT '主键,雪花 ID',
  `channel_id` bigint NOT NULL COMMENT '关联 llm_channels.id',
  `key_cipher` varchar(512) NOT NULL COMMENT 'AES-GCM 密文,v1:base64(iv+密文+tag)',
  `key_mask` varchar(32) NOT NULL COMMENT '展示用掩码,如 sk-ab****cd12',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 正常 / 1 失效(鉴权失败被自动停用) / 2 手动禁用',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '连续失败次数,用于计算冷却时间',
  `cooldown_until` datetime DEFAULT NULL COMMENT '冷却结束时间,期间不参与调度',
  `last_used_at` datetime DEFAULT NULL COMMENT '最后使用时间,按最久未用优先轮询',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_channel_status` (`channel_id`,`status`) COMMENT '按渠道取可用 key'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型 API Key 池(密文存储)';

-- 种子数据:与之前 application.yml 里的五家保持一致,之后改这里(或直接改表)即可
INSERT IGNORE INTO `llm_channels` (`id`, `provider_id`, `name`, `provider_type`, `base_url`, `priority`) VALUES
  (1, 'deepseek', 'DeepSeek',   'OPENAI_COMPAT', 'https://api.deepseek.com/v1', 100),
  (2, 'qwen',     '通义千问',    'OPENAI_COMPAT', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 90),
  (3, 'zhipu',    '智谱 GLM',    'OPENAI_COMPAT', 'https://open.bigmodel.cn/api/paas/v4', 80),
  (4, 'minimax',  'MiniMax',    'OPENAI_COMPAT', 'https://api.minimaxi.com/v1', 70),
  (5, 'doubao',   '豆包',        'OPENAI_COMPAT', 'https://ark.cn-beijing.volces.com/api/v3', 60);

INSERT IGNORE INTO `llm_models` (`id`, `channel_id`, `model_id`, `display_name`, `sort`) VALUES
  (113, 1, 'deepseek-flash',             'DeepSeek V4.1 Flash', 5),
  (101, 1, 'deepseek-chat',              'DeepSeek Chat',      10),
  (114, 1, 'deepseek-v4-pro',            'DeepSeek V4 Pro',    15),
  (102, 1, 'deepseek-reasoner',          'DeepSeek Reasoner',  20),
  (103, 2, 'qwen-plus',                  '千问 Plus',          10),
  (104, 2, 'qwen-max',                   '千问 Max',           20),
  (105, 2, 'qwen-turbo',                 '千问 Turbo',         30),
  (106, 3, 'glm-4-plus',                 'GLM-4 Plus',         10),
  (107, 3, 'glm-4-air',                  'GLM-4 Air',          20),
  (108, 3, 'glm-4-flash',                'GLM-4 Flash',        30),
  (109, 4, 'MiniMax-Text-01',            'MiniMax Text 01',    10),
  (110, 4, 'abab6.5s-chat',              'abab6.5s',           20),
  (111, 5, 'doubao-1-5-pro-32k-250115',  '豆包 1.5 Pro 32k',   10),
  (112, 5, 'doubao-1-5-lite-32k-250115', '豆包 1.5 Lite 32k',  20);

-- DeepSeek 官方提供余额查询接口,标记为自动刷新;其余厂商先手工维护余额
UPDATE `llm_channels` SET `balance_source` = 'AUTO' WHERE `provider_id` = 'deepseek';
