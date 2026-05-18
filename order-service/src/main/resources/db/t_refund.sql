-- 退款申请表
CREATE TABLE `t_refund`
(
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `refund_no`       VARCHAR(64)    NOT NULL COMMENT '退款单号',
    `order_no`        VARCHAR(64)    NOT NULL COMMENT '订单号',
    `user_id`         BIGINT         NOT NULL COMMENT '用户ID',
    `refund_amount`   DECIMAL(10, 2) NOT NULL COMMENT '退款金额',
    `refund_reason`   VARCHAR(500)            DEFAULT NULL COMMENT '退款原因',
    `status`          TINYINT        NOT NULL DEFAULT 0 COMMENT '退款状态: 0-处理中 1-退款成功 2-退款失败 3-已拒绝',
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
  DEFAULT CHARSET = utf8mb4 COMMENT ='退款申请表';
