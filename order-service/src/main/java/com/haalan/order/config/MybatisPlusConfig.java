package com.haalan.order.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.haalan.common.utils.UserContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

	/**
	 * 动态表名插件
	 */
	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

		// 动态表名插件
		DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
		dynamicTableNameInnerInterceptor.setTableNameHandler((sql, tableName) -> {
			// 只处理 t_seckill_order 表
			if ("t_seckill_order".equals(tableName)) {
				// 从ThreadLocal获取分表键
				Long userId = UserContext.getUser();
				if (userId != null) {
					// 按用户ID取模分表
					long tableIndex = userId % 2;
					return "t_seckill_order_" + tableIndex;
				}
			}
			return tableName;
		});

		interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
		return interceptor;
	}
}