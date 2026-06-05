-- =====================================================================
-- 合并建表脚本（基于实际数据库结构导出）
-- 服务器: 192.168.100.133:3306
-- =====================================================================


-- =====================================================================
-- 一、user-db（用户库）
-- =====================================================================
CREATE DATABASE IF NOT EXISTS `user-db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `user-db`;

-- -------------------------------------
-- 1. 用户表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_user`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`           VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`           VARCHAR(128) NOT NULL COMMENT '密码(加密)',
    `phone`              VARCHAR(20)  NOT NULL COMMENT '手机号',
    `email`              VARCHAR(50)  DEFAULT NULL COMMENT '邮箱',
    `avatar`             VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status`             TINYINT      DEFAULT '1' COMMENT '状态: 0-禁用 1-正常',
    `member_level`       TINYINT      DEFAULT '0' COMMENT '会员等级: 0-普通 1-白银 2-黄金 3-钻石',
    `token_version`      INT          DEFAULT '1' COMMENT 'Token版本号',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `avatar_update_time` DATETIME     DEFAULT NULL COMMENT '头像更新时间',
    `token_acquire_time` DATETIME     DEFAULT NULL COMMENT '临时凭证获取时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户表';

-- -------------------------------------
-- 2. 用户地址表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_user_address`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址ID',
    `user_id`        BIGINT       NOT NULL COMMENT '用户ID',
    `receiver_name`  VARCHAR(50)  NOT NULL COMMENT '收货人姓名',
    `receiver_phone` VARCHAR(20)  NOT NULL COMMENT '收货人电话',
    `province`       VARCHAR(50)  NOT NULL COMMENT '省份',
    `city`           VARCHAR(50)  NOT NULL COMMENT '城市',
    `district`       VARCHAR(50)  NOT NULL COMMENT '区/县',
    `detail_address` VARCHAR(255) NOT NULL COMMENT '详细地址',
    `is_default`     TINYINT  DEFAULT '0' COMMENT '是否默认: 0-否 1-是',
    `deleted`        TINYINT  DEFAULT '0' COMMENT '是否删除: 0-未删除 1-已删除',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户地址表';

-- -------------------------------------
-- 3. 用户登录日志表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_user_login_log`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`      BIGINT      NOT NULL COMMENT '用户ID',
    `username`     VARCHAR(50) NOT NULL COMMENT '用户名',
    `login_ip`     VARCHAR(50)          DEFAULT NULL COMMENT '登录IP地址',
    `login_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `login_result` TINYINT(1)  NOT NULL DEFAULT '1' COMMENT '登录结果：0-失败 1-成功',
    `fail_reason`  VARCHAR(200)         DEFAULT NULL COMMENT '失败原因',
    `browser`      VARCHAR(100)         DEFAULT NULL COMMENT '浏览器信息',
    `os`           VARCHAR(100)         DEFAULT NULL COMMENT '操作系统',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_login_time` (`login_time`),
    KEY `idx_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户登录日志表';

-- Seata AT undo_log
CREATE TABLE IF NOT EXISTS `undo_log`
(
    `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
    `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
    `log_status`    INT          NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AT transaction mode undo table';


-- =====================================================================
-- 二、item-db（商品库）
-- =====================================================================
CREATE DATABASE IF NOT EXISTS `item-db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `item-db`;

-- -------------------------------------
-- 1. 品牌表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_brand`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '品牌ID',
    `name`        VARCHAR(100) NOT NULL COMMENT '品牌名称',
    `status`      TINYINT  DEFAULT '1' COMMENT '状态',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='品牌表';

-- -------------------------------------
-- 2. 商品分类表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_category`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `parent_id`   BIGINT   DEFAULT '0' COMMENT '父分类ID',
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
    `level`       TINYINT  DEFAULT '1' COMMENT '分类层级',
    `sort`        INT      DEFAULT '0' COMMENT '排序',
    `status`      TINYINT  DEFAULT '1' COMMENT '状态: 0-禁用 1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品分类表';

-- -------------------------------------
-- 3. SPU（标准商品）
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_spu`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'SPU ID',
    `spu_code`    VARCHAR(50)  NOT NULL COMMENT 'SPU编码',
    `name`        VARCHAR(100) NOT NULL COMMENT '商品名称',
    `category_id` BIGINT       NOT NULL COMMENT '分类ID',
    `brand_id`    BIGINT       DEFAULT NULL COMMENT '品牌ID',
    `description` TEXT COMMENT '商品描述',
    `main_image`  VARCHAR(255) DEFAULT NULL COMMENT '主图URL',
    `images`      JSON         DEFAULT NULL COMMENT '商品图片列表',
    `status`      TINYINT      DEFAULT '1' COMMENT '状态: 0-下架 1-上架',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spu_code` (`spu_code`),
    KEY `idx_category_id` (`category_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品SPU表';

-- -------------------------------------
-- 4. SKU（库存单元）
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_sku`
(
    `id`                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
    `sku_code`            VARCHAR(50)    NOT NULL COMMENT 'SKU编码',
    `spu_id`              BIGINT         NOT NULL COMMENT 'SPU ID',
    `name`                VARCHAR(100)   NOT NULL COMMENT 'SKU名称',
    `specifications`      JSON                    DEFAULT NULL COMMENT '规格属性',
    `price`               DECIMAL(10, 2) NOT NULL COMMENT '原价',
    `promotion_price`     DECIMAL(10, 2)          DEFAULT NULL COMMENT '促销价',
    `stock`               INT            NOT NULL DEFAULT '0' COMMENT '库存数量',
    `locked_stock`        INT                     DEFAULT '0' COMMENT '锁定库存(防超卖)',
    `sold_count`          INT                     DEFAULT '0' COMMENT '销量',
    `version`             INT                     DEFAULT '0' COMMENT '乐观锁版本',
    `images`              JSON                    DEFAULT NULL COMMENT 'SKU图片',
    `status`              TINYINT                 DEFAULT '1' COMMENT '状态: 0-禁用 1-正常',
    `alipay_product_code` VARCHAR(50)             DEFAULT NULL COMMENT '支付宝产品code',
    `wechat_product_code` VARCHAR(50)             DEFAULT NULL COMMENT '微信产品code',
    `create_time`         DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_code` (`sku_code`),
    KEY `idx_spu_id` (`spu_id`),
    KEY `idx_stock` (`stock`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品SKU表';

-- Seata AT undo_log
CREATE TABLE IF NOT EXISTS `undo_log`
(
    `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
    `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
    `log_status`    INT          NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AT transaction mode undo table';


-- =====================================================================
-- 三、order-db（订单库）
-- =====================================================================
CREATE DATABASE IF NOT EXISTS `order-db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `order-db`;

-- -------------------------------------
-- 1. 订单主表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_order`
(
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`        VARCHAR(32)    NOT NULL COMMENT '订单号',
    `user_id`         BIGINT         NOT NULL COMMENT '用户ID',
    `total_amount`    DECIMAL(10, 2) NOT NULL COMMENT '订单总金额',
    `discount_amount` DECIMAL(10, 2) DEFAULT '0.00' COMMENT '优惠金额',
    `actual_amount`   DECIMAL(10, 2) NOT NULL COMMENT '实付金额',
    `order_type`      TINYINT        DEFAULT '1' COMMENT '订单类型: 1-普通 2-秒杀 3-团购',
    `status`          TINYINT        DEFAULT '0' COMMENT '订单状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    `address_id`      BIGINT         DEFAULT NULL COMMENT '收货地址ID',
    `receiver_info`   JSON           DEFAULT NULL COMMENT '收货人信息快照',
    `remark`          VARCHAR(500)   DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单主表';

-- -------------------------------------
-- 2. 订单商品明细表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_order_item`
(
    `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `order_id`       BIGINT         NOT NULL COMMENT '订单ID',
    `order_no`       VARCHAR(32)    NOT NULL COMMENT '订单号',
    `sku_id`         BIGINT         NOT NULL COMMENT 'SKU ID',
    `sku_code`       VARCHAR(50)    NOT NULL COMMENT 'SKU编码',
    `product_name`   VARCHAR(100)   NOT NULL COMMENT '商品名称',
    `product_image`  VARCHAR(255) DEFAULT NULL COMMENT '商品图片',
    `price`          DECIMAL(10, 2) NOT NULL COMMENT '单价',
    `quantity`       INT            NOT NULL COMMENT '数量',
    `total_price`    DECIMAL(10, 2) NOT NULL COMMENT '总价',
    `specifications` JSON         DEFAULT NULL COMMENT '规格信息',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单商品明细表';

-- -------------------------------------
-- 3. 支付表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_payment`
(
    `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `order_no`       VARCHAR(32)    DEFAULT NULL COMMENT '订单号',
    `user_id`        BIGINT         DEFAULT NULL COMMENT '用户ID',
    `pay_amount`     DECIMAL(10, 2) DEFAULT NULL COMMENT '支付金额',
    `pay_type`       TINYINT        DEFAULT NULL COMMENT '支付方式',
    `status`         TINYINT        DEFAULT '0' COMMENT '支付状态',
    `transaction_id` VARCHAR(64)    DEFAULT NULL COMMENT '第三方流水',
    `pay_time`       DATETIME       DEFAULT NULL COMMENT '支付时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`) COMMENT '订单唯一'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='支付表';

-- -------------------------------------
-- 4. 退款申请表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_refund`
(
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `refund_no`       VARCHAR(64)    NOT NULL COMMENT '退款单号',
    `order_no`        VARCHAR(64)    NOT NULL COMMENT '订单号',
    `user_id`         BIGINT         NOT NULL COMMENT '用户ID',
    `refund_amount`   DECIMAL(10, 2) NOT NULL COMMENT '退款金额',
    `refund_reason`   VARCHAR(500)            DEFAULT NULL COMMENT '退款原因',
    `status`          TINYINT        NOT NULL DEFAULT '0' COMMENT '退款状态: 0-处理中 1-退款成功 2-退款失败 3-已拒绝',
    `trade_no`        VARCHAR(64)             DEFAULT NULL COMMENT '支付宝交易号',
    `refund_trade_no` VARCHAR(64)             DEFAULT NULL COMMENT '支付宝退款流水号',
    `reject_reason`   VARCHAR(500)            DEFAULT NULL COMMENT '拒绝原因',
    `apply_time`      DATETIME                DEFAULT NULL COMMENT '申请时间',
    `handle_time`     DATETIME                DEFAULT NULL COMMENT '处理时间',
    `complete_time`   DATETIME                DEFAULT NULL COMMENT '完成时间',
    `create_time`     DATETIME                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_apply_time` (`apply_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='退款申请表';

-- -------------------------------------
-- 5. 秒杀订单表 0（userId % 2 == 0）
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_seckill_order_0`
(
    `id`                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`            VARCHAR(32)    NOT NULL COMMENT '订单号',
    `user_id`             BIGINT         NOT NULL COMMENT '用户ID',
    `activity_id`         BIGINT         NOT NULL COMMENT '活动ID',
    `seckill_product_id`  BIGINT         DEFAULT NULL COMMENT '秒杀商品ID',
    `sku_id`              BIGINT         NOT NULL COMMENT 'SKU',
    `product_name`        VARCHAR(200)   DEFAULT NULL COMMENT '商品名称',
    `seckill_price`       DECIMAL(10, 2) NOT NULL COMMENT '秒杀价格',
    `quantity`            INT            DEFAULT '1' COMMENT '购买数量',
    `total_amount`        DECIMAL(10, 2) DEFAULT NULL COMMENT '总金额',
    `status`              TINYINT        DEFAULT '0' COMMENT '状态：0-待支付，1-已支付，2-已取消',
    `order_time`          DATETIME       DEFAULT NULL COMMENT '下单时间',
    `pay_time`            DATETIME       DEFAULT NULL COMMENT '支付时间',
    `cancel_time`         DATETIME       DEFAULT NULL COMMENT '取消时间',
    `cancel_reason`       VARCHAR(100)   DEFAULT NULL COMMENT '取消原因',
    `create_time`         DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `alipay_product_code` VARCHAR(50)    DEFAULT NULL COMMENT '支付宝产品code',
    `wechat_product_code` VARCHAR(50)    DEFAULT NULL COMMENT '微信产品code',
    `address_id`          VARCHAR(50)    DEFAULT NULL COMMENT '地址id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`) COMMENT '订单号唯一',
    KEY `idx_user_activity_sku` (`user_id`, `activity_id`, `sku_id`) COMMENT '查询索引',
    KEY `idx_create_time` (`create_time`) COMMENT '时间索引'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='秒杀订单表0';

-- -------------------------------------
-- 6. 秒杀订单表 1（userId % 2 == 1）
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_seckill_order_1` LIKE `t_seckill_order_0`;

-- Seata AT undo_log
CREATE TABLE IF NOT EXISTS `undo_log`
(
    `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
    `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
    `log_status`    INT          NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AT transaction mode undo table';


-- =====================================================================
-- 四、seckill-db（秒杀库）
-- =====================================================================
CREATE DATABASE IF NOT EXISTS `seckill-db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `seckill-db`;

-- -------------------------------------
-- 1. 秒杀活动表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_seckill_activity`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动ID',
    `activity_name`  VARCHAR(100) NOT NULL COMMENT '活动名称',
    `start_time`     DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`       DATETIME     NOT NULL COMMENT '结束时间',
    `status`         TINYINT      DEFAULT '0' COMMENT '状态: 0-未开始 1-进行中 2-已结束',
    `activity_desc`  VARCHAR(500) DEFAULT NULL COMMENT '活动描述',
    `limit_per_user` INT          DEFAULT '1' COMMENT '每人限购数量',
    `total_stock`    INT          DEFAULT '0' COMMENT '活动总库存',
    `version`        INT          DEFAULT '0' COMMENT '乐观锁版本号',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_time_status` (`start_time`, `end_time`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='秒杀活动表';

-- -------------------------------------
-- 2. 秒杀商品表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_seckill_product`
(
    `id`                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `activity_id`         BIGINT         NOT NULL COMMENT '活动ID',
    `sku_id`              BIGINT         NOT NULL COMMENT '商品SKU ID',
    `sku_code`            VARCHAR(50)    NOT NULL COMMENT 'SKU编码',
    `product_name`        VARCHAR(100)   NOT NULL COMMENT '商品名称',
    `original_price`      DECIMAL(10, 2) NOT NULL COMMENT '原价',
    `seckill_price`       DECIMAL(10, 2) NOT NULL COMMENT '秒杀价',
    `stock`               INT            NOT NULL COMMENT '秒杀库存',
    `locked_stock`        INT         DEFAULT '0' COMMENT '锁定库存',
    `sold_stock`          INT         DEFAULT '0' COMMENT '已售库存',
    `limit_per_user`      INT         DEFAULT '1' COMMENT '每人限购数量',
    `sort`                INT         DEFAULT '0' COMMENT '排序',
    `version`             INT         DEFAULT '0' COMMENT '乐观锁版本号',
    `alipay_product_code` VARCHAR(50) DEFAULT NULL COMMENT '支付宝产品code',
    `wechat_product_code` VARCHAR(50) DEFAULT NULL COMMENT '微信产品code',
    `create_time`         DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_sku` (`activity_id`, `sku_id`),
    KEY `idx_activity_id` (`activity_id`),
    KEY `idx_stock` (`stock`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='秒杀商品表';

-- -------------------------------------
-- 3. 秒杀日志表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_seckill_log`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `activity_id` BIGINT      NOT NULL COMMENT '活动ID',
    `sku_id`      BIGINT      NOT NULL COMMENT 'SKU ID',
    `action`      VARCHAR(50) NOT NULL COMMENT '操作类型: REQUEST/LOCK/SUCCESS/FAIL',
    `ip`          VARCHAR(50)  DEFAULT NULL COMMENT 'IP地址',
    `user_agent`  VARCHAR(255) DEFAULT NULL COMMENT '用户代理',
    `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
    `cost_time`   INT          DEFAULT NULL COMMENT '耗时(ms)',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `product_id`  BIGINT      NOT NULL COMMENT 'PRODUCT ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_activity` (`user_id`, `activity_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='秒杀日志表';

-- -------------------------------------
-- 4. 用户秒杀记录表 0（userId % 2 == 0）
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `user_seckill_record_0`
(
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`        VARCHAR(64)    NOT NULL COMMENT '订单号',
    `user_id`         BIGINT         NOT NULL COMMENT '用户ID',
    `activity_id`     BIGINT         NOT NULL COMMENT '活动ID',
    `activity_name`   VARCHAR(128)            DEFAULT NULL COMMENT '活动名称',
    `product_id`      BIGINT                  DEFAULT NULL COMMENT '商品ID',
    `product_name`    VARCHAR(256)   NOT NULL COMMENT '商品名称',
    `product_image`   VARCHAR(512)            DEFAULT NULL COMMENT '商品图片URL',
    `seckill_price`   DECIMAL(10, 2) NOT NULL COMMENT '秒杀价格',
    `quantity`        INT            NOT NULL DEFAULT '1' COMMENT '购买数量',
    `status`          TINYINT        NOT NULL DEFAULT '0' COMMENT '订单状态：0-待支付，1-已支付，2-已取消，3-已完成',
    `create_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `pay_expire_time` DATETIME                DEFAULT NULL COMMENT '支付过期时间',
    `update_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      TINYINT        NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_activity_id` (`activity_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_user_create` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户秒杀记录表-0';

-- -------------------------------------
-- 5. 用户秒杀记录表 1（userId % 2 == 1）
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `user_seckill_record_1` LIKE `user_seckill_record_0`;

-- Seata AT undo_log
CREATE TABLE IF NOT EXISTS `undo_log`
(
    `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
    `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
    `log_status`    INT          NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AT transaction mode undo table';


-- =====================================================================
-- 五、seata（Seata TC 服务端）
-- =====================================================================
CREATE DATABASE IF NOT EXISTS `seata` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `seata`;

CREATE TABLE IF NOT EXISTS `global_table`
(
    `xid`                       VARCHAR(128) NOT NULL,
    `transaction_id`            BIGINT        DEFAULT NULL,
    `status`                    TINYINT      NOT NULL,
    `application_id`            VARCHAR(32)   DEFAULT NULL,
    `transaction_service_group` VARCHAR(32)   DEFAULT NULL,
    `transaction_name`          VARCHAR(128)  DEFAULT NULL,
    `timeout`                   INT           DEFAULT NULL,
    `begin_time`                BIGINT        DEFAULT NULL,
    `application_data`          VARCHAR(2000) DEFAULT NULL,
    `gmt_create`                DATETIME      DEFAULT NULL,
    `gmt_modified`              DATETIME      DEFAULT NULL,
    PRIMARY KEY (`xid`),
    KEY `idx_status_gmt_modified` (`status`, `gmt_modified`),
    KEY `idx_transaction_id` (`transaction_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `branch_table`
(
    `branch_id`         BIGINT       NOT NULL,
    `xid`               VARCHAR(128) NOT NULL,
    `transaction_id`    BIGINT        DEFAULT NULL,
    `resource_group_id` VARCHAR(32)   DEFAULT NULL,
    `resource_id`       VARCHAR(256)  DEFAULT NULL,
    `branch_type`       VARCHAR(8)    DEFAULT NULL,
    `status`            TINYINT       DEFAULT NULL,
    `client_id`         VARCHAR(64)   DEFAULT NULL,
    `application_data`  VARCHAR(2000) DEFAULT NULL,
    `gmt_create`        DATETIME(6)   DEFAULT NULL,
    `gmt_modified`      DATETIME(6)   DEFAULT NULL,
    PRIMARY KEY (`branch_id`),
    KEY `idx_xid` (`xid`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `lock_table`
(
    `row_key`        VARCHAR(128) NOT NULL,
    `xid`            VARCHAR(128)          DEFAULT NULL,
    `transaction_id` BIGINT                DEFAULT NULL,
    `branch_id`      BIGINT       NOT NULL,
    `resource_id`    VARCHAR(256)          DEFAULT NULL,
    `table_name`     VARCHAR(32)           DEFAULT NULL,
    `pk`             VARCHAR(36)           DEFAULT NULL,
    `status`         TINYINT      NOT NULL DEFAULT '0' COMMENT '0:locked ,1:rollbacking',
    `gmt_create`     DATETIME              DEFAULT NULL,
    `gmt_modified`   DATETIME              DEFAULT NULL,
    PRIMARY KEY (`row_key`),
    KEY `idx_status` (`status`),
    KEY `idx_branch_id` (`branch_id`),
    KEY `idx_xid_and_branch_id` (`xid`, `branch_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `distributed_lock`
(
    `lock_key`   CHAR(20)    NOT NULL,
    `lock_value` VARCHAR(20) NOT NULL,
    `expire`     BIGINT DEFAULT NULL,
    PRIMARY KEY (`lock_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 分布式锁初始数据
INSERT INTO `distributed_lock` (lock_key, lock_value, expire)
VALUES ('AsyncCommitting', ' ', 0),
       ('RetryCommitting', ' ', 0),
       ('RetryRollbacking', ' ', 0),
       ('TxTimeoutCheck', ' ', 0);


-- =====================================================================
-- 六、测试数据
-- =====================================================================
USE `item-db`;

-- 商品分类
INSERT INTO `t_category` (`id`, `parent_id`, `name`, `level`, `sort`, `status`)
VALUES (1, 0, '服装', 1, 1, 1),
       (2, 0, '数码', 1, 2, 1),
       (3, 0, '食品', 1, 3, 1),
       (4, 1, '男装', 2, 1, 1),
       (5, 1, '女装', 2, 2, 1),
       (6, 2, '手机', 2, 1, 1),
       (7, 2, '电脑', 2, 2, 1),
       (8, 3, '零食', 2, 1, 1),
       (9, 3, '饮料', 2, 2, 1),
       (10, 4, 'T恤', 3, 1, 1),
       (11, 4, '牛仔裤', 3, 2, 1),
       (12, 5, '连衣裙', 3, 1, 1),
       (13, 6, '智能手机', 3, 1, 1),
       (14, 6, '功能机', 3, 2, 0),
       (15, 7, '轻薄本', 3, 1, 1),
       (16, 7, '游戏本', 3, 2, 1);

-- 品牌
INSERT INTO `t_brand` (`id`, `name`, `status`)
VALUES (1, '优衣库', 1),
       (2, '耐克', 1),
       (3, '苹果', 1),
       (4, '华为', 1),
       (5, '联想', 1),
       (6, '三只松鼠', 1),
       (7, '可口可乐', 1),
       (8, '小米', 1),
       (9, 'ZARA', 0);

-- SPU
INSERT INTO `t_spu` (`id`, `spu_code`, `name`, `category_id`, `brand_id`, `description`, `main_image`, `images`,
                     `status`)
VALUES (1, 'SPU001', '优衣库纯棉T恤', 10, 1, '基础款纯棉T恤，舒适透气', 'https://img.example.com/uniqlo_tee_main.jpg', '[
  "https://img.example.com/uniqlo_tee_1.jpg",
  "https://img.example.com/uniqlo_tee_2.jpg"
]', 1),
       (2, 'SPU002', 'iPhone 15 Pro', 13, 3, '苹果最新旗舰手机，A17芯片', 'https://img.example.com/iphone15pro_main.jpg',
        '[
          "https://img.example.com/iphone15pro_1.jpg",
          "https://img.example.com/iphone15pro_2.jpg"
        ]', 1),
       (3, 'SPU003', '华为 Mate 60 Pro', 13, 4, '华为旗舰手机，卫星通话', 'https://img.example.com/mate60pro_main.jpg',
        '[
          "https://img.example.com/mate60pro_1.jpg",
          "https://img.example.com/mate60pro_2.jpg"
        ]', 1),
       (4, 'SPU004', '联想小新 Pro 16', 15, 5, '高性能轻薄本', 'https://img.example.com/lenovo_xiaoxin_main.jpg', '[
         "https://img.example.com/lenovo_xiaoxin_1.jpg"
       ]', 1),
       (5, 'SPU005', '三只松鼠每日坚果', 8, 6, '混合坚果礼盒，每日一包', 'https://img.example.com/nuts_main.jpg', '[
         "https://img.example.com/nuts_1.jpg"
       ]', 1),
       (6, 'SPU006', '可口可乐经典罐', 9, 7, '经典口味汽水', 'https://img.example.com/coke_main.jpg', '[
         "https://img.example.com/coke_1.jpg"
       ]', 1),
       (7, 'SPU007', '耐克 Air Max 运动鞋', 4, 2, '经典气垫跑鞋', 'https://img.example.com/nike_airmax_main.jpg', '[
         "https://img.example.com/nike_airmax_1.jpg"
       ]', 1),
       (8, 'SPU008', 'ZARA 连衣裙', 12, 9, '夏季新款碎花裙', 'https://img.example.com/zara_dress_main.jpg', NULL, 0);

-- SKU
INSERT INTO `t_sku` (`id`, `sku_code`, `spu_id`, `name`, `specifications`, `price`, `promotion_price`, `stock`,
                     `locked_stock`, `sold_count`, `version`, `images`, `status`)
VALUES (1, 'SKU001001', 1, '优衣库纯棉T恤 白色 S', '{
  "颜色": "白色",
  "尺码": "S"
}', 79.00, 59.00, 100, 5, 234, 0, '[
  "https://img.example.com/sku001001_1.jpg"
]', 1),
       (2, 'SKU001002', 1, '优衣库纯棉T恤 白色 M', '{
         "颜色": "白色",
         "尺码": "M"
       }', 79.00, 59.00, 150, 2, 500, 0, '[
         "https://img.example.com/sku001002_1.jpg"
       ]', 1),
       (3, 'SKU001003', 1, '优衣库纯棉T恤 黑色 L', '{
         "颜色": "黑色",
         "尺码": "L"
       }', 79.00, 59.00, 0, 0, 120, 0, '[
         "https://img.example.com/sku001003_1.jpg"
       ]', 1),
       (4, 'SKU001004', 1, '优衣库纯棉T恤 蓝色 XL', '{
         "颜色": "蓝色",
         "尺码": "XL"
       }', 79.00, 79.00, 80, 10, 45, 0, NULL, 1),
       (5, 'SKU002001', 2, 'iPhone 15 Pro 128GB 原色钛金属', '{
         "颜色": "原色钛金属",
         "容量": "128GB"
       }', 7999.00, 7499.00, 50, 20, 1200, 2, '[
         "https://img.example.com/iphone15_128_natural.jpg"
       ]', 1),
       (6, 'SKU002002', 2, 'iPhone 15 Pro 256GB 蓝色钛金属', '{
         "颜色": "蓝色钛金属",
         "容量": "256GB"
       }', 8999.00, 8499.00, 30, 5, 800, 1, '[
         "https://img.example.com/iphone15_256_blue.jpg"
       ]', 1),
       (7, 'SKU002003', 2, 'iPhone 15 Pro 512GB 黑色钛金属', '{
         "颜色": "黑色钛金属",
         "容量": "512GB"
       }', 10999.00, 10499.00, 15, 2, 300, 0, '[
         "https://img.example.com/iphone15_512_black.jpg"
       ]', 1),
       (8, 'SKU003001', 3, '华为 Mate 60 Pro 12+256GB 雅川青', '{
         "颜色": "雅川青",
         "配置": "12+256GB"
       }', 6499.00, 6499.00, 0, 0, 5000, 5, '[
         "https://img.example.com/mate60_256_green.jpg"
       ]', 1),
       (9, 'SKU003002', 3, '华为 Mate 60 Pro 12+512GB 白沙银', '{
         "颜色": "白沙银",
         "配置": "12+512GB"
       }', 6999.00, 6799.00, 25, 8, 2100, 3, '[
         "https://img.example.com/mate60_512_silver.jpg"
       ]', 1),
       (10, 'SKU003003', 3, '华为 Mate 60 Pro 12+1TB 雅丹黑', '{
         "颜色": "雅丹黑",
         "配置": "12+1TB"
       }', 7999.00, 7799.00, 10, 1, 600, 1, NULL, 1),
       (11, 'SKU004001', 4, '联想小新 Pro 16 i5/16G/512G 集显', '{
         "处理器": "i5-13500H",
         "内存": "16GB",
         "硬盘": "512GB SSD",
         "显卡": "集显"
       }', 5299.00, 4999.00, 45, 10, 890, 0, '[
         "https://img.example.com/xiaoxin_i5.jpg"
       ]', 1),
       (12, 'SKU004002', 4, '联想小新 Pro 16 i7/32G/1TB 独显', '{
         "处理器": "i7-13700H",
         "内存": "32GB",
         "硬盘": "1TB SSD",
         "显卡": "RTX 4050"
       }', 7999.00, 7699.00, 20, 5, 230, 0, '[
         "https://img.example.com/xiaoxin_i7.jpg"
       ]', 1),
       (13, 'SKU005001', 5, '三只松鼠每日坚果 750g/30包', '{
         "规格": "750g",
         "包装": "礼盒装"
       }', 139.00, 99.00, 500, 150, 12500, 8, '[
         "https://img.example.com/nuts_750g.jpg"
       ]', 1),
       (14, 'SKU005002', 5, '三只松鼠每日坚果 400g/15包', '{
         "规格": "400g",
         "包装": "袋装"
       }', 79.00, 69.00, 300, 20, 4500, 2, '[
         "https://img.example.com/nuts_400g.jpg"
       ]', 1),
       (15, 'SKU006001', 6, '可口可乐 330ml*24罐', '{
         "规格": "330ml",
         "包装": "箱装"
       }', 59.90, 49.90, 1000, 50, 23000, 0, '[
         "https://img.example.com/coke_24.jpg"
       ]', 1),
       (16, 'SKU006002', 6, '可口可乐 2L*6瓶', '{
         "规格": "2L",
         "包装": "箱装"
       }', 39.90, 35.90, 600, 10, 8000, 0, '[
         "https://img.example.com/coke_2l.jpg"
       ]', 1),
       (17, 'SKU006003', 6, '可口可乐 500ml*12瓶', '{
         "规格": "500ml",
         "包装": "箱装"
       }', 29.90, 29.90, 800, 0, 12000, 0, NULL, 1),
       (18, 'SKU007001', 7, '耐克 Air Max 黑白 42码', '{
         "颜色": "黑/白",
         "尺码": "42"
       }', 799.00, 699.00, 60, 12, 350, 1, '[
         "https://img.example.com/airmax_42.jpg"
       ]', 1),
       (19, 'SKU007002', 7, '耐克 Air Max 黑白 43码', '{
         "颜色": "黑/白",
         "尺码": "43"
       }', 799.00, 699.00, 45, 8, 280, 0, '[
         "https://img.example.com/airmax_43.jpg"
       ]', 1),
       (20, 'SKU007003', 7, '耐克 Air Max 全黑 41码', '{
         "颜色": "全黑",
         "尺码": "41"
       }', 799.00, 759.00, 30, 5, 120, 0, NULL, 1),
       (21, 'SKU007004', 7, '耐克 Air Max 全黑 44码', '{
         "颜色": "全黑",
         "尺码": "44"
       }', 799.00, 759.00, 0, 0, 90, 0, '[
         "https://img.example.com/airmax_44.jpg"
       ]', 0),
       (22, 'SKU008001', 8, 'ZARA 碎花连衣裙 M', '{
         "颜色": "碎花",
         "尺码": "M"
       }', 399.00, 299.00, 20, 0, 55, 0, NULL, 0);
