# RabbitMQ配置说明

## 概述

本文档说明了秒杀系统中RabbitMQ的完整配置，包括交换机、队列和绑定关系。

## 配置类

所有RabbitMQ配置都在 `RabbitMQConfig` 类中定义。

## 配置详情

### 1. 订单相关配置

#### 1.1 订单交换机
- **名称**: `seckill.order.exchange`
- **类型**: DirectExchange
- **持久化**: 是
- **自动删除**: 否

#### 1.2 订单队列
- **名称**: `seckill.order.queue`
- **持久化**: 是
- **死信交换机**: `seckill.order.dlx.exchange`
- **死信路由键**: `seckill.order.dlx.routingkey`

#### 1.3 订单绑定
- **交换机**: `seckill.order.exchange`
- **队列**: `seckill.order.queue`
- **路由键**: `success`

#### 1.4 订单死信交换机
- **名称**: `seckill.order.dlx.exchange`
- **类型**: DirectExchange
- **持久化**: 是

#### 1.5 订单死信队列
- **名称**: `seckill.order.dlx.queue`
- **持久化**: 是

#### 1.6 订单死信绑定
- **交换机**: `seckill.order.dlx.exchange`
- **队列**: `seckill.order.dlx.queue`
- **路由键**: `seckill.order.dlx.routingkey`

### 2. 订单超时相关配置

#### 2.1 订单超时队列（延时队列）
- **名称**: `seckill.order.timeout.queue`
- **持久化**: 是
- **TTL**: 15分钟（900000毫秒）
- **死信交换机**: `seckill.timeout.dlx.exchange`
- **死信路由键**: `seckill.timeout.dlx`

**说明**: 此队列没有绑定交换机，消息直接发送到队列。消息在队列中等待15分钟后，如果未被消费，会转发到死信交换机。

#### 2.2 超时死信交换机
- **名称**: `seckill.timeout.dlx.exchange`
- **类型**: DirectExchange
- **持久化**: 是

#### 2.3 超时死信队列
- **名称**: `seckill.timeout.dlx.queue`
- **持久化**: 是
- **死信交换机**: `seckill.timeout.backup.dlx.exchange`
- **死信路由键**: `seckill.timeout.backup.dlx`

#### 2.4 超时死信绑定
- **交换机**: `seckill.timeout.dlx.exchange`
- **队列**: `seckill.timeout.dlx.queue`
- **路由键**: `seckill.timeout.dlx`

#### 2.5 备用死信交换机
- **名称**: `seckill.timeout.backup.dlx.exchange`
- **类型**: DirectExchange
- **持久化**: 是

#### 2.6 备用死信队列
- **名称**: `seckill.timeout.backup.dlx.queue`
- **持久化**: 是

#### 2.7 备用死信绑定
- **交换机**: `seckill.timeout.backup.dlx.exchange`
- **队列**: `seckill.timeout.backup.dlx.queue`
- **路由键**: `seckill.timeout.backup.dlx`

### 3. 用户行为日志相关配置

#### 3.1 日志交换机
- **名称**: `seckill.log.exchange`
- **类型**: TopicExchange
- **持久化**: 是
- **自动删除**: 否

**说明**: 使用TopicExchange可以支持更灵活的路由规则，便于后续扩展不同类型的日志。

#### 3.2 日志队列
- **名称**: `seckill.log.queue`
- **持久化**: 是

#### 3.3 日志绑定
- **交换机**: `seckill.log.exchange`
- **队列**: `seckill.log.queue`
- **路由键**: `seckill.log`

## 消息流转图

### 订单消息流转
```
生产者 -> seckill.order.exchange (Direct)
         |
         | routing_key: success
         v
    seckill.order.queue
         |
         | (消息处理失败或过期)
         v
    seckill.order.dlx.exchange (Direct)
         |
         | routing_key: seckill.order.dlx.routingkey
         v
    seckill.order.dlx.queue
```

### 订单超时消息流转
```
生产者 -> seckill.order.timeout.queue (直接发送，无交换机)
         |
         | (等待15分钟TTL)
         v
    seckill.timeout.dlx.exchange (Direct)
         |
         | routing_key: seckill.timeout.dlx
         v
    seckill.timeout.dlx.queue
         |
         | (可选：再次转发到备用死信)
         v
    seckill.timeout.backup.dlx.exchange (Direct)
         |
         | routing_key: seckill.timeout.backup.dlx
         v
    seckill.timeout.backup.dlx.queue
```

### 日志消息流转
```
生产者 -> seckill.log.exchange (Topic)
         |
         | routing_key: seckill.log
         v
    seckill.log.queue
         |
         v
    消费者（日志处理服务）
```

## 配置特点

### 1. 持久化
所有交换机和队列都设置为持久化，确保RabbitMQ重启后配置和数据不会丢失。

### 2. 死信机制
- 订单队列配置了死信交换机，处理失败的消息会转到死信队列
- 超时队列利用TTL和死信机制实现延时消息功能
- 备用死信提供额外的容错能力

### 3. 灵活性
- 日志使用TopicExchange，支持通配符路由，便于后续扩展
- 订单使用DirectExchange，精确匹配路由键

## 使用示例

### 发送订单消息
```java
rabbitTemplate.convertAndSend(
    RabbitConstants.SECKILL_ORDER_EXCHANGE,
    RabbitConstants.SECKILL_ORDER_ROUTING_KEY,
    orderMessage
);
```

### 发送超时消息
```java
// 直接发送到队列，不需要交换机
rabbitTemplate.convertAndSend(
    RabbitConstants.ORDER_TIMEOUT_QUEUE,
    timeoutMessage
);
```

### 发送日志消息
```java
rabbitTemplate.convertAndSend(
    RabbitConstants.SECKILL_LOG_EXCHANGE,
    RabbitConstants.SECKILL_LOG_ROUTING_KEY,
    logMessage
);
```

## 监控和管理

### 1. RabbitMQ Management Plugin
访问 `http://localhost:15672` 可以查看：
- 交换机列表和配置
- 队列列表和消息数量
- 绑定关系
- 消息流转情况

### 2. 关键指标
- 队列长度：监控积压消息数量
- 消息速率：监控生产和消费速度
- 死信队列：监控失败消息数量

### 3. 告警建议
- 队列长度超过阈值（如10000）
- 死信队列有消息堆积
- 消费者长时间未消费

## 注意事项

1. **队列命名**: 使用统一的命名规范，便于管理和识别
2. **路由键设计**: 保持简洁明了，避免过于复杂
3. **TTL设置**: 根据业务需求合理设置消息过期时间
4. **死信处理**: 需要实现死信队列的消费者，处理失败消息
5. **幂等性**: 消费者需要实现幂等处理，避免重复消费

## 扩展建议

1. **日志分类**: 可以使用不同的路由键区分不同类型的日志
   - `seckill.log.success`: 成功日志
   - `seckill.log.failed`: 失败日志
   - `seckill.log.error`: 错误日志

2. **优先级队列**: 可以为重要消息设置优先级

3. **消息追踪**: 启用RabbitMQ的追踪插件，便于问题排查

4. **集群部署**: 生产环境建议使用RabbitMQ集群提高可用性
