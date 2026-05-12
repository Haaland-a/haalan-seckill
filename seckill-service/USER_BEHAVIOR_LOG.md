# 用户秒杀行为日志功能说明

## 功能概述

实现了用户秒杀行为的日志记录功能，通过MQ异步发送日志消息，包含用户IP、User-Agent等关键信息。

## 实现方式

### 1. 日志消息结构

创建了`SeckillUserLogMessage`类，包含以下字段：

- **logId**: 日志唯一ID（UUID）
- **userId**: 用户ID
- **activityId**: 活动ID
- **seckillProductId**: 秒杀商品ID
- **result**: 操作结果（SUCCESS/FAILED）
- **failReason**: 失败原因
- **userIp**: 用户IP地址
- **userAgent**: 浏览器User-Agent
- **deviceId**: 设备ID（预留字段）
- **requestId**: 请求ID
- **operateTime**: 操作时间
- **remark**: 备注信息

### 2. 日志记录时机

在秒杀执行的三个关键点记录日志：

1. **库存扣减后**：记录库存扣减结果
   ```java
   logToMq(userId, activityId, seckillProductId, result, request.getRequestId(), null);
   ```

2. **业务异常时**：记录失败原因
   ```java
   logToMq(userId, activityId, seckillProductId, -1L, requestId, failReason);
   ```

3. **系统异常时**：记录系统错误
   ```java
   logToMq(userId, activityId, seckillProductId, -99L, requestId, "系统异常");
   ```

### 3. 用户信息获取

使用`WebUtils`工具类获取用户信息：

```java
// 获取用户IP地址
String userIp = WebUtils.getRemoteAddr();

// 获取HttpServletRequest
HttpServletRequest request = WebUtils.getRequest();

// 获取User-Agent
String userAgent = request != null ? request.getHeader("User-Agent") : "";
```

### 4. MQ配置

#### 4.1 常量定义

在`RabbitConstants`中添加了日志相关的常量：

```java
// 交换机
public static final String SECKILL_LOG_EXCHANGE = "seckill.log.exchange";
// 队列
public static final String SECKILL_LOG_QUEUE = "seckill.log.queue";
// 路由
public static final String SECKILL_LOG_ROUTING_KEY = "seckill.log";
```

#### 4.2 RabbitMQ配置类

创建了`RabbitMQConfig`配置类，定义了交换机、队列和绑定关系：

```java
@Configuration
public class RabbitMQConfig {

	/**
	 * 日志交换机（Topic类型）
	 */
	@Bean
	public TopicExchange seckillLogExchange() {
		return new TopicExchange(RabbitConstants.SECKILL_LOG_EXCHANGE, true, false);
	}

	/**
	 * 日志队列（持久化）
	 */
	@Bean
	public Queue seckillLogQueue() {
		return QueueBuilder.durable(RabbitConstants.SECKILL_LOG_QUEUE).build();
	}

	/**
	 * 日志交换机与队列绑定
	 */
	@Bean
	public Binding seckillLogBinding() {
		return BindingBuilder.bind(seckillLogQueue())
				.to(seckillLogExchange())
				.with(RabbitConstants.SECKILL_LOG_ROUTING_KEY);
	}
}
```

### 5. 日志发送逻辑

```java
private void logToMq(Long userId, Long activityId, Long seckillProductId,
                     Long stockResult, String requestId, String failReason) {
    try {
        // 1. 获取用户IP和User-Agent
        String userIp = WebUtils.getRemoteAddr();
        HttpServletRequest request = WebUtils.getRequest();
        String userAgent = request != null ? request.getHeader("User-Agent") : "";

        // 2. 判断操作结果
        String result;
        if (stockResult == null || stockResult < 0) {
            result = "FAILED";
            // 根据stockResult设置failReason
        } else {
            result = "SUCCESS";
            failReason = null;
        }

        // 3. 构建日志消息
        SeckillUserLogMessage logMessage = SeckillUserLogMessage.builder()
                .logId(UUID.fastUUID().toString(true))
                .userId(userId)
                .activityId(activityId)
                .seckillProductId(seckillProductId)
                .result(result)
                .failReason(failReason)
                .userIp(userIp)
                .userAgent(userAgent)
                .requestId(requestId)
                .operateTime(LocalDateTime.now())
                .build();

        // 4. 发送日志到MQ
        rabbitTemplate.convertAndSend(
                RabbitConstants.SECKILL_LOG_EXCHANGE,
                RabbitConstants.SECKILL_LOG_ROUTING_KEY,
                logMessage
        );

        log.info("用户秒杀行为日志已发送, logId={}, userId={}, result={}",
                logId, userId, result);

    } catch (Exception e) {
        // 日志记录失败不影响主流程
        log.error("发送用户行为日志失败, userId={}, activityId={}",
                userId, activityId, e);
    }
}
```

## 关键特性

### 1. 异步记录
- 通过MQ异步发送日志，不阻塞主流程
- 日志记录失败不影响秒杀业务

### 2. 完整信息
- 用户IP地址：用于防刷、地域分析
- User-Agent：用于设备识别、浏览器统计
- 请求ID：用于链路追踪
- 操作结果：成功/失败及原因

### 3. 容错处理
- 日志发送异常被捕获，不影响主业务流程
- 记录错误日志便于问题排查

## 使用场景

### 1. 风控分析
- 检测异常IP的频繁请求
- 识别恶意刷单行为
- 分析用户地域分布

### 2. 数据统计
- 秒杀成功率统计
- 失败原因分析
- 用户行为分析

### 3. 问题排查
- 通过requestId追踪完整请求链路
- 分析特定用户的操作历史
- 定位系统问题

## MQ消息示例

### 成功消息
```json
{
  "logId": "a1b2c3d4e5f6",
  "userId": 10001,
  "activityId": 30001,
  "seckillProductId": 50001,
  "result": "SUCCESS",
  "failReason": null,
  "userIp": "192.168.1.100",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...",
  "requestId": "req_20260101_103000_001",
  "operateTime": "2026-05-12T10:30:00",
  "remark": null
}
```

### 失败消息（库存不足）
```json
{
  "logId": "a1b2c3d4e5f7",
  "userId": 10002,
  "activityId": 30001,
  "seckillProductId": 50001,
  "result": "FAILED",
  "failReason": "STOCK_NOT_ENOUGH",
  "userIp": "192.168.1.101",
  "userAgent": "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0)...",
  "requestId": "req_20260101_103001_002",
  "operateTime": "2026-05-12T10:30:01",
  "remark": null
}
```

## 注意事项

1. **性能考虑**：日志记录是异步的，不会影响秒杀性能
2. **数据一致性**：日志可能在极端情况下丢失（MQ故障），但不影响业务
3. **隐私保护**：IP地址等敏感信息需要妥善保护
4. **存储策略**：建议设置日志过期时间，避免数据无限增长

## 后续优化建议

1. **批量发送**：可以累积一定数量的日志后批量发送到MQ
2. **日志过滤**：可以配置只记录特定类型的日志（如只记录失败）
3. **实时监控**：可以对接实时监控系统，及时发现异常
4. **数据归档**：定期将历史日志归档到冷存储
