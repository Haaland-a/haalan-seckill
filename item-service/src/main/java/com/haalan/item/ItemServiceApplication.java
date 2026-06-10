package com.haalan.item;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.haalan.item.mapper")
@EnableFeignClients(basePackages = "com.haalan.api.client")
public class ItemServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ItemServiceApplication.class, args);
	}
}