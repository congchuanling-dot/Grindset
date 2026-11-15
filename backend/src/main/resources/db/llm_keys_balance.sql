-- 多 Key + 余额支持(对已有库的增量迁移,只执行一次)
-- 执行:mysql --default-character-set=utf8mb4 -uroot -p123456 grindset < llm_keys_balance.sql
-- 新库直接执行 llm_catalog.sql 即可,不用跑这个文件。

-- 1. 同一 provider 允许建多条渠道(多账号),所以去掉 (provider_id, base_url) 唯一约束
ALTER TABLE `llm_channels`
  DROP INDEX `uk_provider_base`,
  ADD KEY `idx_provider` (`provider_id`,`base_url`),
  ADD COLUMN `balance` decimal(14,4) DEFAULT NULL COMMENT '账户余额,未知为 NULL',
  ADD COLUMN `balance_currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '币种,如 CNY/USD',
  ADD COLUMN `balance_source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 手工维护 / AUTO 定时查询',
  ADD COLUMN `balance_updated_at` datetime DEFAULT NULL COMMENT '余额最后更新时间';

-- 2. Key 池:一个渠道可挂多把 key,存密文
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

-- 3. DeepSeek 官方有余额查询接口,标记为自动;其余厂商先手工维护
UPDATE `llm_channels` SET `balance_source` = 'AUTO' WHERE `provider_id` = 'deepseek';
