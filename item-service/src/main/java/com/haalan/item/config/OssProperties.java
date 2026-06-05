package com.haalan.item.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

	private String endpoint;

	private String accessKeyId;

	private String accessKeySecret;

	private String bucketName;

	private String filePrefix = "item";

	private Long stsDuration = 900L;

	private String host;

	private String roleArn;

	private String regionId = "cn-beijing";
}
