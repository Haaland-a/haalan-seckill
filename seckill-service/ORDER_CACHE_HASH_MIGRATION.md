# 订单缓存结构优化说明

## 修改概述

将订单缓存从JSON字符串存储改为Redis Hash结构存储。

## 修改原因

1. **更细粒度的数据访问**：Hash结构可以单独获取某个字段，而不需要反序列化整个JSON
2. **更好的内存管理**：Redis对Hash结构有更优化的内存使用
3. **便于扩展**：添加新字段时不需要重新序列化整个对象
4. **性能优势**：对于部分字段查询场景，Hash结构更高效

## 修改内容

### 1. 存储方式变更

**修改前（JSON字符串）**：
```java
String orderJson = JSONUtil.toJsonStr(orderVO);
redisTemplate.opsForValue().set(
    orderCacheKey,
    orderJson,
    SeckillConstants.IDEMPOTENT_EXPIRE_SECONDS,
    TimeUnit.SECONDS
);
```

**修改后（Hash结构）**：
```java
Map<String, Object> orderMap = new HashMap<>();
orderMap.put("orderNo", orderVO.getOrderNo());
orderMap.put("seckillProductId", orderVO.getSeckillProductId());
orderMap.put("productName", orderVO.getProductName());
orderMap.put("seckillPrice", orderVO.getSeckillPrice().toString());
orderMap.put("quantity", orderVO.getQuantity());
orderMap.put("totalAmount", orderVO.getTotalAmount().toString());
orderMap.put("payExpireTime", orderVO.getPayExpireTime().toString());
orderMap.put("status", orderVO.getStatus());
orderMap.put("statusName", orderVO.getStatusName());

redisTemplate.opsForHash().putAll(orderCacheKey, orderMap);
redisTemplate.expire(orderCacheKey, SeckillConstants.IDEMPOTENT_EXPIRE_SECONDS, TimeUnit.SECONDS);
```

### 2. 读取方式变更

**修改前**：
```java
String orderJson = redisTemplate.opsForValue().get(orderCacheKey);
SeckillOrderVO orderVO = JSONUtil.toBean(orderJson, SeckillOrderVO.class);
```

**修改后**：
```java
Map<Object, Object> orderHash = redisTemplate.opsForHash().entries(orderCacheKey);
SeckillOrderVO orderVO = convertHashToOrderVO(orderHash);
```

### 3. 新增辅助方法

添加了`convertHashToOrderVO`方法，用于将Hash数据转换为SeckillOrderVO对象：

```java
private SeckillOrderVO convertHashToOrderVO(Map<Object, Object> orderHash) {
    return SeckillOrderVO.builder()
            .orderNo(orderHash.get("orderNo") != null ? orderHash.get("orderNo").toString() : null)
            .seckillProductId(orderHash.get("seckillProductId") != null ? 
                    Long.parseLong(orderHash.get("seckillProductId").toString()) : null)
            .productName(orderHash.get("productName") != null ? orderHash.get("productName").toString() : null)
            .seckillPrice(orderHash.get("seckillPrice") != null ? 
                    new BigDecimal(orderHash.get("seckillPrice").toString()) : null)
            .quantity(orderHash.get("quantity") != null ? 
                    Integer.parseInt(orderHash.get("quantity").toString()) : null)
            .totalAmount(orderHash.get("totalAmount") != null ? 
                    new BigDecimal(orderHash.get("totalAmount").toString()) : null)
            .payExpireTime(orderHash.get("payExpireTime") != null ? 
                    LocalDateTime.parse(orderHash.get("payExpireTime").toString()) : null)
            .status(orderHash.get("status") != null ? 
                    Integer.parseInt(orderHash.get("status").toString()) : null)
            .statusName(orderHash.get("statusName") != null ? orderHash.get("statusName").toString() : null)
            .build();
}
```

## Redis数据结构对比

### 修改前（String）
```
Key: seckill:order:SK1234567890
Value: {"orderNo":"SK1234567890","seckillProductId":1001,"productName":"iPhone 16",...}
```

### 修改后（Hash）
```
Key: seckill:order:SK1234567890
Fields:
  - orderNo: "SK1234567890"
  - seckillProductId: "1001"
  - productName: "iPhone 16 Pro Max 256GB 深空黑"
  - seckillPrice: "6999.00"
  - quantity: "1"
  - totalAmount: "6999.00"
  - payExpireTime: "2026-01-01T10:45:00"
  - status: "0"
  - statusName: "待支付"
```

## 涉及的文件

1. `SeckillExecuteServiceImpl.java`
   - 修改了`createPreOrder`方法中的缓存存储逻辑
   - 修改了`getExistingResult`方法中的缓存读取逻辑
   - 修改了`queryResult`方法中的缓存读取逻辑
   - 新增了`convertHashToOrderVO`辅助方法

## 注意事项

1. **数据类型转换**：Hash中所有值都存储为String，读取时需要手动转换为对应类型
2. **空值处理**：在转换方法中增加了null检查，避免空指针异常
3. **向后兼容**：此修改不兼容旧的JSON格式缓存，建议清空旧缓存或等待其自然过期
4. **性能考虑**：Hash结构在字段较多时可能有轻微的性能开销，但对于订单这种固定字段的场景非常合适

## 测试建议

1. 执行秒杀成功后，检查Redis中的订单缓存结构
2. 查询秒杀结果，验证能正确读取Hash格式的订单信息
3. 验证各种数据类型（Long、BigDecimal、LocalDateTime等）的转换是否正确
4. 测试空值情况的处理

## 优势总结

- ✅ 支持字段级别的查询和更新
- ✅ 更好的内存使用效率
- ✅ 便于后续扩展和维护
- ✅ 符合Redis最佳实践
