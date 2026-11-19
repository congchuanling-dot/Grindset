-- 登录限流:记一次失败(原子 INCR + 首次设 TTL)
-- KEYS[1] = 当前桶 key
-- ARGV[1] = TTL 毫秒
-- 返回:当前桶累计次数
local n = redis.call('INCR', KEYS[1])
if n == 1 then
  -- 只在首次写入时设置过期,避免每次续命导致 key 永不过期
  redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return n
