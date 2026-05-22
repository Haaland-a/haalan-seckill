package com.haalan.seckill.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean("seckillLogExecutor")
	public Executor seckillLogExecutor() {

		ThreadPoolTaskExecutor executor =
				new ThreadPoolTaskExecutor();

		// 根据CPU核心数动态配置（假设8核CPU）
		int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
		executor.setCorePoolSize(corePoolSize);

		executor.setMaxPoolSize(corePoolSize * 4);

		executor.setQueueCapacity(5000);

		executor.setKeepAliveSeconds(60);

		executor.setThreadNamePrefix("seckill-log-");

		executor.setRejectedExecutionHandler(
				new ThreadPoolExecutor.CallerRunsPolicy()
		);

		executor.initialize();

		return executor;
	}
}