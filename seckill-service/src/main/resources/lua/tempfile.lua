--[[
秒杀执行 Lua 脚本 - 原子性扣减库存和更新购买记录

KEYS[1]: 库存key (seckill:stock:{activityId}:{seckillProductId})
KEYS[2]: 用户购买记录key (seckill:buy:{userId})
KEYS[3]: 活动限购key (seckill:limit:activity:{activityId})
KEYS[4]: 商品限购key (seckill:limit:{userId}:{seckillProductId})

ARGV[1]: 购买数量
ARGV[2]: 用户ID
ARGV[3]: 活动ID
ARGV[4]: 秒杀商品ID

返回值:
>= 0: 扣减成功，返回剩余库存
-1: 库存不足
-2: 超过商品限购
-3: 超过活动限购
]]

-- 获取参数
local stockKey = KEYS[1]
local userBuyKey = KEYS[2]
local activityLimitKey = KEYS[3]
local productLimitKey = KEYS[4]

local quantity = tonumber(ARGV[1])
local userId = ARGV[2]
local activityId = ARGV[3]
local seckillProductId = ARGV[4]

-- 1. 检查库存是否充足
local stock = redis.call('get', stockKey)
if not stock then
    return -1  -- 库存key不存在，视为库存不足
end

stock = tonumber(stock)
if stock < quantity then
    return -1  -- 库存不足
end

-- 2. 检查商品级限购
local productLimit = redis.call('get', productLimitKey)
if productLimit then
    productLimit = tonumber(productLimit)
    local productBuyField = "product:" .. seckillProductId
    local productBuy = redis.call('hget', userBuyKey, productBuyField)
    productBuy = productBuy and tonumber(productBuy) or 0

    if (productBuy + quantity) > productLimit then
        return -2  -- 超过商品限购
    end
end

-- 3. 检查活动级限购
local activityLimit = redis.call('get', activityLimitKey)
if activityLimit then
    activityLimit = tonumber(activityLimit)
    local activityBuyField = "activity:" .. activityId
    local activityBuy = redis.call('hget', userBuyKey, activityBuyField)
    activityBuy = activityBuy and tonumber(activityBuy) or 0

    if (activityBuy + quantity) > activityLimit then
        return -3  -- 超过活动限购
    end
end

-- 4. 扣减库存
local remainingStock = redis.call('decrby', stockKey, quantity)

-- 5. 更新用户购买记录
redis.call('hincrby', userBuyKey, "product:" .. seckillProductId, quantity)
redis.call('hincrby', userBuyKey, "activity:" .. activityId, quantity)

-- 6. 返回剩余库存
return remainingStock