package com.haalan.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云OSS配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

	/**
	 * OSS访问域名（Endpoint）
	 */
	private String endpoint;

	/**
	 * AccessKey ID
	 */
	private String accessKeyId;

	/**
	 * AccessKey Secret
	 */
	private String accessKeySecret;

	/**
	 * Bucket名称
	 */
	private String bucketName;

	/**
	 * 文件存储路径前缀
	 */
	private String filePrefix = "avatar";

	/**
	 * STS临时凭证有效期（秒），默认900秒（15分钟）
	 */
	private Long stsDuration = 900L;

	/**
	 * OSS访问域名（用于前端直传）
	 */
	private String host;

	/**
	 * 角色ARN（用于STS临时凭证）
	 * 格式：acs:ram::${AccountId}:role/${RoleName}
	 */
	private String roleArn;

	/**
	 * 区域ID
	 */
	private String regionId = "cn-beijing";
}
