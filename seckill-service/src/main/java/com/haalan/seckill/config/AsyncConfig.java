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

		executor.setCorePoolSize(8);

		executor.setMaxPoolSize(16);

		executor.setQueueCapacity(2000);

		executor.setKeepAliveSeconds(60);

		executor.setThreadNamePrefix("seckill-log-");

		executor.setRejectedExecutionHandler(
				new ThreadPoolExecutor.CallerRunsPolicy()
		);

		executor.initialize();

		return executor;
	}
}