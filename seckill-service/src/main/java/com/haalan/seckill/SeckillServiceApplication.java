package com.haalan.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;

@EnableFeignClients(basePackages = "com.haalan.api.client")
@SpringBootApplication
@MapperScan("com.haalan.seckill.mapper")
@EnableScheduling
public class SeckillServiceApplication {
	private final Environment environment;

	public SeckillServiceApplication(Environment environment) {
		this.environment = environment;
	}

	@PostConstruct
	public void checkConfig() {
		System.out.println("MQ Host: " + environment.getProperty("haalan.mq.host"));
		System.out.println("RabbitMQ Host: " + environment.getProperty("spring.rabbitmq.host"));
	}

	public static void main(String[] args) {
		SpringApplication.run(SeckillServiceApplication.class, args);
	}

}
