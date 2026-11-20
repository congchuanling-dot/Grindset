-- 注册限流:双桶加权估算 + 原子计数(检查通过才累加)
-- KEYS[1] = 上一桶 key, KEYS[2] = 当前桶 key
-- ARGV[1] = 桶长(秒), ARGV[2] = 当前 epoch 秒, ARGV[3] = 阈值, ARGV[4] = TTL 毫秒
-- 返回:本次之后的估算次数;返回 -1 表示超限(本次不计数)
local prev = tonumber(redis.call('GET', KEYS[1]) or '0')
local cur = tonumber(redis.call('GET', KEYS[2]) or '0')
local bucket = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
-- 上一桶按「剩余时间比例」衰减:刚切桶权重为 1,桶末尾趋近 0
local estimate = math.floor(prev * ((bucket - now % bucket) / bucket)) + cur
if estimate >= tonumber(ARGV[3]) then
  return -1
end
local n = redis.call('INCR', KEYS[2])
if n == 1 then
  -- 只在首次写入时设置过期,避免每次续命导致 key 永不过期
  redis.call('PEXPIRE', KEYS[2], tonumber(ARGV[4]))
end
return estimate + 1
