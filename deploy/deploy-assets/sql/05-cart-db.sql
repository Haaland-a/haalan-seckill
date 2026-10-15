-- =====================================================================
--  haalan 购物车库（cart-db）
--  ---------------------------------------------------------------------
--  用途：为普通商品购买链路提供购物车能力（cart-service 使用）。
--  可重复执行：CREATE DATABASE / CREATE TABLE 都带 IF NOT EXISTS，
--  重复执行不会报错、不会丢数据。
--
--  在已运行的虚拟机上单独执行：
--      docker exec -i mysql8 mysql -uroot -p123456 < 05-cart-db.sql
--
--  或直接用内联语句（推荐，避免密码进 history）：
--      docker exec -i mysql8 mysql -uroot -p123456 -e "source /tmp/05-cart-db.sql"
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `cart-db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `cart-db`;

-- -------------------------------------
-- 购物车明细表
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `t_cart_item` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '购物车项ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `sku_id`      BIGINT      NOT NULL COMMENT 'SKU ID',
    `spu_id`      BIGINT      DEFAULT NULL COMMENT 'SPU ID（冗余，便于按商品聚合）',
    `quantity`    INT         NOT NULL DEFAULT '1' COMMENT '数量',
    `selected`    TINYINT     NOT NULL DEFAULT '1' COMMENT '是否勾选：0-未选，1-已选',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 同一用户同一 SKU 只允许一行，加购时累加数量（业务层依赖此唯一键兜底并发）
    UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车明细表';

-- -------------------------------------
-- Seata AT 模式 undo_log（cart-service 参与分布式事务时需要）
-- -------------------------------------
CREATE TABLE IF NOT EXISTS `undo_log` (
    `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
    `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
    `log_status`    INT          NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AT transaction mode undo table';
