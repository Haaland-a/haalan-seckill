# 秒杀结果查询接口文档

## 接口信息

**接口地址**: `GET /api/seckill/result/{requestId}`

**认证方式**: Bearer Token

**请求参数**:
- `requestId` (路径参数): 请求ID，执行秒杀时传入的唯一标识

## 响应格式

### 1. 处理中状态

```json
{
    "code": 2001,
    "message": "处理中",
    "data": {
        "requestId": "req_20260101_103000_001",
        "status": "PROCESSING",
        "orderNo": null,
        "orderInfo": null,
        "failReason": null
    },
    "timestamp": 1704067200000
}
```

### 2. 秒杀成功状态

```json
{
    "code": 200,
    "message": "秒杀成功",
    "data": {
        "requestId": "req_20260101_103000_001",
        "status": "SUCCESS",
        "orderNo": "SK20260101103000001",
        "orderInfo": {
            "orderNo": "SK20260101103000001",
            "seckillProductId": 1001,
            "productName": "iPhone 16 Pro Max 256GB 深空黑",
            "seckillPrice": 6999.00,
            "quantity": 1,
            "totalAmount": 6999.00,
            "payExpireTime": "2026-01-01 10:45:00",
            "status": 0,
            "statusName": "待支付"
        },
        "failReason": null
    },
    "timestamp": 1704067200000
}
```

### 3. 秒杀失败状态 - 库存不足

```json
{
    "code": 1003,
    "message": "库存不足",
    "data": {
        "requestId": "req_20260101_103000_001",
        "status": "FAILED",
        "orderNo": null,
        "orderInfo": null,
        "failReason": "STOCK_NOT_ENOUGH"
    },
    "timestamp": 1704067200000
}
```

### 4. 秒杀失败状态 - 活动已结束

```json
{
    "code": 1004,
    "message": "活动已结束",
    "data": {
        "requestId": "req_20260101_103000_001",
        "status": "FAILED",
        "orderNo": null,
        "orderInfo": null,
        "failReason": "ACTIVITY_ENDED"
    },
    "timestamp": 1704067200000
}
```

### 5. 秒杀失败状态 - 活动未开始

```json
{
    "code": 1005,
    "message": "活动未开始",
    "data": {
        "requestId": "req_20260101_103000_001",
        "status": "FAILED",
        "orderNo": null,
        "orderInfo": null,
        "failReason": "ACTIVITY_NOT_STARTED"
    },
    "timestamp": 1704067200000
}
```

### 6. 秒杀失败状态 - 活动已过期

```json
{
    "code": 1007,
    "message": "活动已过期",
    "data": {
        "requestId": "req_20260101_103000_001",
        "status": "FAILED",
        "orderNo": null,
        "orderInfo": null,
        "failReason": "ACTIVITY_EXPIRED"
    },
    "timestamp": 1704067200000
}
```

### 7. 秒杀失败状态 - 其他原因

```json
{
    "code": 1006,
    "message": "商品限购",
    "data": {
        "requestId": "req_20260101_103000_001",
        "status": "FAILED",
        "orderNo": null,
        "orderInfo": null,
        "failReason": "商品限购"
    },
    "timestamp": 1704067200000
}
```

## 状态说明

- **PROCESSING**: 处理中，前端应继续轮询
- **SUCCESS**: 秒杀成功，返回订单信息
- **FAILED**: 秒杀失败，返回失败原因

## 失败原因说明

- **STOCK_NOT_ENOUGH**: 库存不足
- **ACTIVITY_ENDED**: 活动已结束
- **ACTIVITY_NOT_STARTED**: 活动未开始
- **ACTIVITY_EXPIRED**: 活动已过期（Redis中无数据）
- **商品限购**: 已达到该商品的购买上限
- **活动限购**: 已达到该活动的购买上限
- **系统异常**: 系统内部错误

## 使用建议

1. 前端应在执行秒杀后，立即开始轮询此接口
2. 建议轮询间隔为2-3秒
3. 当返回状态为SUCCESS或FAILED时，停止轮询
4. 当返回code为1007（活动已过期）时，提示用户活动已结束

## 实现原理

1. 从Redis的幂等键（`seckill:idempotent:{requestId}`）中查询处理状态
2. 如果键不存在，说明请求ID无效或活动已过期
3. 根据存储的值判断状态：
   - `SUCCESS:{orderNo}`: 秒杀成功
   - `FAILED:{reason}`: 秒杀失败
   - `ERROR:{reason}`: 系统错误
   - 其他值: 处理中
4. 成功时，从订单Hash缓存中获取完整订单信息
   - Redis Key: `seckill:order:{orderNo}`
   - 存储结构: Hash
   - 字段: orderNo, seckillProductId, productName, seckillPrice, quantity, totalAmount, payExpireTime, status, statusName