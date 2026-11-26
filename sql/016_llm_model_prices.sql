-- ============================================================
-- 大模型单价种子数据
-- 单位:元/千 token(官网标价均为元/百万 token,这里除以 1000)
-- cached_input_price 为缓存命中输入单价;不支持缓存的模型留 0
-- 数据来源(2026-11-26):
--   DeepSeek:  https://api-docs.deepseek.com/zh-cn/quick_start/pricing/
--   阿里百炼:  https://help.aliyun.com/zh/model-studio/developer-reference/billing-for-tongyiqianwen
--   智谱:      https://docs.bigmodel.cn/cn/guide/start/pricing
--   火山方舟:  https://www.volcengine.com/docs/82379/1544106
--   MiniMax:   https://platform.minimaxi.com/docs/guides/pricing-paygo
-- 脚本可重复执行,直接覆盖当前单价
-- ============================================================

USE grindset;

-- ---------- DeepSeek(取高峰时段价,空闲时段为半价) ----------
-- deepseek-flash: 输入 2 元/百万,缓存命中 0.04 元/百万,输出 8 元/百万
UPDATE llm_models SET input_price = 0.002000, cached_input_price = 0.000040, output_price = 0.008000
WHERE model_id = 'deepseek-flash';

-- deepseek-v4-pro: 输入 9 元/百万,缓存命中 0.3 元/百万,输出 27 元/百万
UPDATE llm_models SET input_price = 0.009000, cached_input_price = 0.000300, output_price = 0.027000
WHERE model_id = 'deepseek-v4-pro';

-- deepseek-chat(旧名,按 V3 定价):输入 2 元/百万,缓存命中 0.5 元/百万,输出 8 元/百万
UPDATE llm_models SET input_price = 0.002000, cached_input_price = 0.000500, output_price = 0.008000
WHERE model_id = 'deepseek-chat';

-- deepseek-reasoner:输入 4 元/百万,缓存命中 1 元/百万,输出 16 元/百万
UPDATE llm_models SET input_price = 0.004000, cached_input_price = 0.001000, output_price = 0.016000
WHERE model_id = 'deepseek-reasoner';

-- ---------- 通义千问(中国内地,首档价格) ----------
-- qwen-max: 输入 2.4 元/百万,输出 9.6 元/百万
UPDATE llm_models SET input_price = 0.002400, cached_input_price = 0, output_price = 0.009600
WHERE model_id = 'qwen-max';

-- qwen-plus: 输入 0.8 元/百万,输出 2 元/百万(非思考模式)
UPDATE llm_models SET input_price = 0.000800, cached_input_price = 0, output_price = 0.002000
WHERE model_id = 'qwen-plus';

-- qwen-turbo: 输入 0.3 元/百万,输出 0.6 元/百万
UPDATE llm_models SET input_price = 0.000300, cached_input_price = 0, output_price = 0.000600
WHERE model_id = 'qwen-turbo';

-- ---------- 智谱 GLM ----------
-- glm-4-plus: 输入 5 元/百万,输出 5 元/百万,缓存命中 2.5 元/百万
UPDATE llm_models SET input_price = 0.005000, cached_input_price = 0.002500, output_price = 0.005000
WHERE model_id = 'glm-4-plus';

-- glm-4-air: 输入 0.5 元/百万,输出 0.5 元/百万,缓存命中 0.25 元/百万
UPDATE llm_models SET input_price = 0.000500, cached_input_price = 0.000250, output_price = 0.000500
WHERE model_id = 'glm-4-air';

-- glm-4-flash: 官方免费
UPDATE llm_models SET input_price = 0, cached_input_price = 0, output_price = 0
WHERE model_id = 'glm-4-flash';

-- ---------- 火山方舟 豆包(在线推理) ----------
-- doubao-1.5-pro-32k: 输入 0.8 元/百万,缓存命中 0.16 元/百万,输出 2 元/百万
UPDATE llm_models SET input_price = 0.000800, cached_input_price = 0.000160, output_price = 0.002000
WHERE model_id = 'doubao-1-5-pro-32k-250115';

-- doubao-1.5-lite-32k: 输入 0.3 元/百万,缓存命中 0.06 元/百万,输出 0.6 元/百万
UPDATE llm_models SET input_price = 0.000300, cached_input_price = 0.000060, output_price = 0.000600
WHERE model_id = 'doubao-1-5-lite-32k-250115';

-- ---------- MiniMax ----------
-- MiniMax-Text-01: 开源时官方定价,输入 1 元/百万,输出 8 元/百万
UPDATE llm_models SET input_price = 0.001000, cached_input_price = 0, output_price = 0.008000
WHERE model_id = 'MiniMax-Text-01';

-- abab6.5s-chat: 历史模型,阿里云百炼渠道限时免费;直连平台已逐步下线
UPDATE llm_models SET input_price = 0, cached_input_price = 0, output_price = 0
WHERE model_id = 'abab6.5s-chat';
