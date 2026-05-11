package com.haalan.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.haalan.api.client")
@SpringBootApplication
@MapperScan("com.haalan.seckill.mapper")
public class SeckillServiceApplication {


	public static void main(String[] args) {
		SpringApplication.run(SeckillServiceApplication.class, args);
	}

}
