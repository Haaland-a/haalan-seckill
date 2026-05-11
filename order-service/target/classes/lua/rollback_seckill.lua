-- 秒杀订单回滚脚本
-- KEYS[1]: 库存key (seckill:stock:{activityId}:{seckillProductId})
-- KEYS[2]: 用户购买记录key (seckill:buy:{userId})
-- KEYS[3]: 商品限购key (seckill:limit:{userId}:{seckillProductId})
-- KEYS[4]: 活动限购key (seckill:limit:activity:{activityId})
-- ARGV[1]: 回滚数量
-- ARGV[2]: 商品ID (用于构建field)
-- ARGV[3]: 活动ID (用于构建field)

local quantity = tonumber(ARGV[1])
local productId = ARGV[2]
local activityId = ARGV[3]

-- 参数校验
if quantity == nil or quantity <= 0 then
    return -1
end

-- 1. 回滚库存
local currentStock = redis.call('get', KEYS[1])
if currentStock then
    redis.call('incrby', KEYS[1], quantity)
end

-- 2. 回滚用户商品购买记录
local productField = "product:" .. productId
local productBuy = redis.call('hget', KEYS[2], productField)
if productBuy then
    local newProductBuy = tonumber(productBuy) - quantity
    if newProductBuy <= 0 then
        redis.call('hdel', KEYS[2], productField)
    else
        redis.call('hset', KEYS[2], productField, newProductBuy)
    end
end

-- 3. 回滚用户活动购买记录
local activityField = "activity:" .. activityId
local activityBuy = redis.call('hget', KEYS[2], activityField)
if activityBuy then
    local newActivityBuy = tonumber(activityBuy) - quantity
    if newActivityBuy <= 0 then
        redis.call('hdel', KEYS[2], activityField)
    else
        redis.call('hset', KEYS[2], activityField, newActivityBuy)
    end
end

-- 4. 释放商品限购名额（删除记录，表示该用户当前没有购买）
-- 注意：如果限购是累计购买数量，这里不需要处理
-- 因为限购key是临时存储用户本次购买的令牌，超时释放即可
if KEYS[3] and redis.call('exists', KEYS[3]) == 1 then
    redis.call('del', KEYS[3])
end

-- 5. 释放活动限购名额
if KEYS[4] and redis.call('exists', KEYS[4]) == 1 then
    redis.call('del', KEYS[4])
end

-- 6. 如果用户购买记录为空，删除整个hash
if redis.call('hlen', KEYS[2]) == 0 then
    redis.call('del', KEYS[2])
end

return 0