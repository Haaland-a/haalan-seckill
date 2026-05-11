package com.haalan.seckill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "haalan.stoken")
@Data
public class STokenProperties {

	private Integer timeOut;

}