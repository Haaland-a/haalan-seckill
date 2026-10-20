# Haalan 电商平台 — 高并发微服务架构实践

Haalan 是一个基于 **Spring Cloud Alibaba** 的完整电商平台，以 **高并发秒杀系统** 为核心亮点。
项目采用微服务架构，涵盖用户、商品、购物车、订单、秒杀、搜索等核心电商链路，融合了分布式事务、多级缓存、消息队列、搜索引擎等主流技术栈。

> **已从纯秒杀演示升级为完整商城**：新增 cart-service 购物车微服务，前端补齐普通商品入口
> （首页分类楼层 / 商品列表 / 商品详情 / 购物车 / 结算页 / 订单列表 / 退款），管理端补齐
> SPU/SKU 上下架、库存、订单与数据统计，形成「普通商品 + 普通购买」的完整闭环。

---

## 性能测试

使用 JMeter 对最复杂的秒杀接口进行 30,000 次请求压测：

| 指标            | 数值         |
|---------------|------------|
| 样本数           | 30,000     |
| 平均响应时间        | 483ms      |
| 中位数           | 442ms      |
| TP90          | 666ms      |
| TP99          | 1,316ms    |
| **吞吐量 (QPS)** | **305.84** |
| **错误率**       | **0.000%** |

---

## 技术栈

| 类别     | 技术                                          |
|--------|---------------------------------------------|
| 基础框架   | Spring Boot 2.7.12, Spring Cloud 2021.0.9   |
| 微服务组件  | Nacos (注册/配置), Sentinel (限流), Seata (分布式事务) |
| API 网关 | Spring Cloud Gateway (动态路由, JWT 认证, 内部接口防护) |
| 数据库    | MySQL 8.0                                   |
| ORM    | MyBatis-Plus 3.5.5                          |
| 缓存     | Redis, ConcurrentHashMap 本地缓存               |
| 消息队列   | RabbitMQ (死信队列, 延时队列)                       |
| 搜索引擎   | Elasticsearch 7.12.1                        |
| 远程调用   | OpenFeign + LoadBalancer                    |
| 安全     | JWT (RSA 非对称加密), BCrypt, RBAC            |
| 文件存储   | 阿里云 OSS (STS 临时凭证, 前端直传)                    |
| 开发工具   | JDK 17, Lombok, Hutool, Guava               |

---

## 系统架构

```
                         haalan-frontend (Vue3)
                               │
                      haalan-gateway (8080)
        JWT 认证 · 动态路由 · RBAC · 内部接口拦截
                               │
   ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┐
   │      │      │      │      │      │      │      │
 user   item  order seckill search  cart haalan  Nacos
 svc    svc    svc    svc    svc    svc  -api   (注册/配置)
   │      │      │      │      │      │
   └──────┴──────┴──────┴──────┴──────┴──────┘
                ┌──────────────────────┐
                │ MySQL · Redis · MQ   │
                │ ES · Sentinel · XXL  │
                └──────────────────────┘
```

## 模块说明

| 模块                | 说明                                                      |
|-------------------|---------------------------------------------------------|
| `haalan-gateway`  | 网关服务 — 统一入口、JWT 认证鉴权、Nacos 动态路由、`/inner/**` 内部接口拦截      |
| `haalan-common`   | 公共组件 — 统一响应、全局异常处理、工具类、图片提取                         |
| `haalan-api`      | OpenFeign 远程调用接口定义 + 熔断降级                             |
| `user-service`    | 用户服务 — 注册/登录/地址管理/OSS直传/管理员功能                           |
| `item-service`    | 商品服务 — SPU/SKU 管理、库存原子扣减、ES 文档同步、索引重建                      |
| `order-service`   | 订单服务 — 普通订单全流程、支付宝支付、退款、数据统计                          |
| `seckill-service` | 秒杀服务 — 高并发秒杀核心 (亮点)                                      |
| `search-service`  | 搜索服务 — 商品全文检索、分类筛选、销量重排                               |
| `cart-service`    | 购物车服务 — 购物车增删改查、勾选结算、失效商品校验、批量回查 SKU                  |

---

## 核心亮点

### 1. 高并发秒杀系统 — 12 层防护架构

秒杀系统的核心设计围绕 "在正确性的前提下追求极致性能"：

```
用户请求
  │
  ├─ ① 幂等性控制 (Redis SETNX)        防止重复提交
  ├─ ② 令牌验证 (一次性 Token)          防刷, 预发资格
  ├─ ③ 活动信息验证 (Redis 预热)        快速校验
  ├─ ④ 商品信息验证 (Redis 预热)        快速校验
  ├─ ⑤ 活动时间窗口校验                 合法性检查
  ├─ ⑥ Redis Lua 原子性库存扣减         核心: 库存 + 限购原子操作
  ├─ ⑦ 预订单生成 & Redis 缓存          快速响应
  ├─ ⑧ 可靠消息投递 (Redis→MQ→回调)    异步解耦, 最终一致
  ├─ ⑨ 延时队列 (15min 超时取消)        自动兜底
  └─ ⑩ 异步用户行为日志                审计分析
```

#### Redis Lua 原子性扣减

```lua
-- 原子性完成：检查库存 + 校验限购 + 扣减 + 记录
local stock = redis.call('GET', KEYS[1])
if not stock or tonumber(stock) <= 0 then return -1 end

local userBuy = redis.call('HGET', KEYS[2], ARGV[1])
if userBuy and tonumber(userBuy) >= tonumber(ARGV[2]) then return -2 end

redis.call('DECR', KEYS[1])
redis.call('HINCRBY', KEYS[2], ARGV[1], 1)
return remaining
```

#### 可靠消息投递

解决 "库存扣减成功，但订单创建失败" 的分布式一致性问题：

1. 库存扣减成功 → 将消息写入 Redis 待确认表
2. 发送 RabbitMQ 消息（附带 CorrelationData）
3. Broker 确认 ACK → 回调删除 Redis 待确认记录
4. 若 ACK 失败 → 定时任务扫描 Redis 补偿投递

### 2. 四层缓存防击穿

```
请求 → ① 布隆过滤器 (Guava, 1% 误判率, 10 万容量)
       ② 本地缓存 (ConcurrentHashMap 售罄标记, 快速失败)
       ③ Redis 缓存 (Hash 结构, 预热数据)
       ④ 缓存空对象 (60s 短 TTL, 防穿透)
       ⑤ 数据库
```

- 布隆过滤器预热时将活动/商品 ID 加载，不存在的数据直接拦截
- 本地缓存 `ConcurrentHashMap` 存储售罄标记，0 网络开销
- 售罄商品本地标记后，无需请求 Redis 即可快速返回
- 定时任务每 10 分钟扫描未来 12 小时内的活动，自动预热

### 3. 网关认证体系

- **JWT + RSA 非对称加密**：私钥签名, 公钥验签, 防篡改
- **Token 版本控制**：用户修改密码/退出后, 旧 Token 立即失效 (Redis 版本比对)
- **RBAC 权限**：网关层拦截 `/api/admin/*`，仅 Token 携带 `admin` claim 的用户可访问
- **内部接口防护**：`InnerApiGuardFilter`（order = -100，早于认证过滤器）拦截一切 `/inner/**`，
  返回 404 而非 403，避免暴露接口存在性
- **动态路由**：监听 Nacos 配置变更, 运行时热更新路由规则

### 4. 分布式事务

使用 Seata AT 模式保证跨服务数据一致性：

```
createOrder()
  ├─ 验证地址 (user-service, OpenFeign)
  ├─ 查询 SKU (item-service, OpenFeign)
  ├─ 批量扣库存 (item-service, 乐观锁)
  ├─ 保存订单 (order-service, MySQL)
  └─ 保存订单明细 (order-service, MySQL)
```

订单取消时自动恢复库存，使用乐观锁防止并发。

### 5. MQ 延时队列 + 多层死信兜底

```
订单超时(15min) → 延时队列(TTL) → 死信交换机 → 死信队列 → 取消订单+恢复库存
                                                        ↓
                                                备用死信队列(告警兜底)
```

秒杀服务包含完整的企业级消息架构：死信 + 延时 + 重试 + 兜底告警，覆盖支付成功、退款成功、订单超时、日志等多种消息场景。

### 6. 阿里云 OSS 前端直传

- STS AssumeRole 获取临时凭证，无需暴露永久 AK/SK
- 最小权限策略：头像限制 `{bucket}/avatar/{userId}/*`，商品图限制 `{bucket}/item/{spuCode}/*`
- 频率控制：1 小时内不可重复获取凭证
- 文件全程不经过 Java 服务，浏览器 `ali-oss` SDK 直传

### 7. Elasticsearch 搜索与商品索引

- **索引以 SPU 为单位**，只存结构字段（价格、规格、图片），**不存库存与销量**（变动太频繁）
- **写操作即时同步**：SPU 下架级联禁用全部 SKU、SKU 上下架/改价同步重建父 SPU 文档
- **不可卖即删文档**：SPU 不存在 / 已下架 / 无上架 SKU → `deleteSpuFromEs`，保证搜索结果天然只含可卖商品
- **缓存与 ES 同步失效**：每次写操作按「先写 DB → 再删缓存」顺序失效 `product:detail:`、`sku:detail:`、`sku-stock:`、`sku-sales:`
- **库存销量回源**：Redis 未命中时 Feign 批量回查 `item-service`，支持价格排序与页内销量重排
- **索引对账入口**：`POST /api/product/inner/rebuildIndex` 以 DB 为准全量重建，清理历史漂移

### 8. 普通购买链路（本次新增）

- **首页**：秒杀活动置顶 + 分类楼层（按一级分类并行拉取商品）+ 全部商品入口
- **商品列表**：三层分类树导航、关键词、价格排序（升降序切换）、分页、URL 同步
- **商品详情**：规格选择、数量步进、库存提示、立即购买 / 加入购物车
- **购物车**：单条/批量/勾选/清空、失效商品置灰（下架、售罄、库存不足）、实时金额
- **结算页**：支持直接购买与购物车结算两种来源
- **订单**：普通订单列表（状态筛选）、详情、取消、退款申请（真实对接 `POST /api/payment/refund`）
- **管理端**：SPU/SKU 上下架、库存修改、订单与退款审核、数据统计

### 9. 管理端运营系统

- **用户管理**：用户列表 / 状态管理 / 登录日志
- **商品管理**：SPU/SKU 管理 / 品牌分类 / 库存管理 / 图片 OSS 直传
- **订单管理**：普通/秒杀订单 / 详情 / 状态流转 / 退款审核
- **数据统计**：平台总览 / 活动销售统计 / 近 N 天趋势分析

---

## 本地运行

```bash
# 环境要求: JDK 17+, Docker, Nacos 2.x
配置数据库

# 1. 启动基础设施 (MySQL, Redis, RabbitMQ, ES, Nacos)
docker-compose up -d

# 2. 确保 Nacos 中存在 gateway-routes.json 配置

# 3. 构建 & 启动
mvn clean install -DskipTests
```

> 需自行配置：Nacos 地址、阿里云 OSS 参数、支付宝参数。
> 购物车服务需要单独建库并执行 `deploy/deploy-assets/sql/05-cart-db.sql`。

---

## 项目沉淀

- **高并发设计**：12 层秒杀防护体系，单机 305 QPS 零错误
- **分布式一致性**：Seata AT + 可靠消息投递 + 本地消息表兜底
- **缓存策略**：布隆过滤器 + 本地缓存 + Redis + 空对象，四层防击穿
- **安全体系**：RSA 签名 JWT + Token 版本控制 + RBAC + 内部接口防护 + 一次性令牌
- **异步解耦**：MQ 削峰填谷 + 延时队列 + 多层死信 + 回调补偿
- **数据一致性**：库存原子扣减、支付金额校验、下架三级同步（DB / 缓存 / ES）、索引对账
- **资源治理**：8 个应用容器分级 JVM 参数（128m~384m 堆 + SerialGC + 直接内存上限），
  单机可用内存由 336MB 提升至约 1.9GB
- **工程实践**：统一响应 / 全局异常 / 操作日志 / 分库分表 / Swagger 文档
