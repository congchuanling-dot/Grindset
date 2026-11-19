-- 登录限流:双桶加权估算(只读,不写入)
-- KEYS[1] = 上一桶 key, KEYS[2] = 当前桶 key
-- ARGV[1] = 桶长(秒), ARGV[2] = 当前 epoch 秒
-- 返回:加权后的近似窗口内失败次数(整数)
local prev = tonumber(redis.call('GET', KEYS[1]) or '0')
local cur = tonumber(redis.call('GET', KEYS[2]) or '0')
local bucket = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local elapsed = now % bucket
-- 上一桶按「剩余时间比例」衰减:刚切桶权重为 1,桶末尾趋近 0
local weight = (bucket - elapsed) / bucket
return math.floor(prev * weight) + cur
