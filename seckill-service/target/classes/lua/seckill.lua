-- seckill.lua
-- KEYS[1] = 库存 key, 如 seckill:stock:10001
-- KEYS[2] = 用户限购 key, 如 seckill:limit:30001:50001
-- ARGV[1] = 用户 ID
-- ARGV[2] = 每人限购数量
-- ARGV[3] = 活动限购 key, 如 seckill:limit:activity:30001
-- ARGV[4] = 活动每人限购数量

-- 返回值：
-- 0  = 扣减成功
-- -1 = 库存不足
-- -2 = 该商品已超过用户限购
-- -3 = 该活动已超过用户限购

local stock = redis.call('GET', KEYS[1])
if not stock or tonumber(stock) <= 0 then
    return -1
end

-- 检查该用户对该商品的购买数量
local userBuyCount = redis.call('HGET', KEYS[2], ARGV[1])
if userBuyCount and tonumber(userBuyCount) >= tonumber(ARGV[2]) then
    return -2
end

-- 检查该用户对该活动的购买数量（如果启用活动级限购）
if ARGV[3] and ARGV[3] ~= '' then
    local activityUserBuyCount = redis.call('HGET', ARGV[3], ARGV[1])
    if activityUserBuyCount and tonumber(activityUserBuyCount) >= tonumber(ARGV[4]) then
        return -3
    end
end

-- 扣减库存
local remaining = redis.call('DECR', KEYS[1])

-- 记录用户购买数量
redis.call('HINCRBY', KEYS[2], ARGV[1], 1)

-- 记录活动级用户购买数量
if ARGV[3] and ARGV[3] ~= '' then
    redis.call('HINCRBY', ARGV[3], ARGV[1], 1)
end

return remaining