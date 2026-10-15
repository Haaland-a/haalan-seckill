-- 用户登录日志表
CREATE TABLE IF NOT EXISTS `t_user_login_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `login_ip` VARCHAR(50) DEFAULT NULL COMMENT '登录IP地址',
  `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `login_result` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '登录结果：0-失败 1-成功',
  `fail_reason` VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
  `browser` VARCHAR(100) DEFAULT NULL COMMENT '浏览器信息',
  `os` VARCHAR(100) DEFAULT NULL COMMENT '操作系统',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_login_time` (`login_time`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录日志表';
