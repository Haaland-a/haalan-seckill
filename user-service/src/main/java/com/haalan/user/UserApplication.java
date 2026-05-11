package com.haalan.user;


import com.haalan.user.config.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


//@EnableFeignClients(basePackages = "com.hmall.api.client", defaultConfiguration = DefaultFeignConfig.class)
//@MapperScan("com.hmall.user.mapper")
@MapperScan("com.haalan.user.mapper")
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
class UserApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserApplication.class, args);
	}

}
