package com.haalan.seckill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "haalan.mq")
@Data
public class MQProperties {
	// 消息超时时间
	private Integer timeOut;

}