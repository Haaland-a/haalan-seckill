package com.haalan.user.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云OSS配置类
 */
@Configuration
@RequiredArgsConstructor
public class OssConfig {

	private final OssProperties ossProperties;

	@Bean
	public OSS ossClient() {
		return new OSSClientBuilder().build(
				ossProperties.getEndpoint(),
				ossProperties.getAccessKeyId(),
				ossProperties.getAccessKeySecret()
		);
	}
}
