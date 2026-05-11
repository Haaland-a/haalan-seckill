package com.haalan.search;

import com.haalan.api.config.DefaultFeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

//为 feign 客户端启用,并配置默认的 Feign 配置类,有拦截器获取登录用户信息
@EnableFeignClients(basePackages = "com.haalan.api.client", defaultConfiguration = DefaultFeignConfig.class)
@SpringBootApplication
public class SearchServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SearchServiceApplication.class, args);
	}

}
