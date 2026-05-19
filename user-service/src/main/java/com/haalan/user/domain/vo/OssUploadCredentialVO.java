package com.haalan.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OSS上传凭证VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "OSS上传凭证", description = "前端直传OSS所需凭证")
public class OssUploadCredentialVO {

	@ApiModelProperty(value = "AccessKey ID")
	@JsonProperty("accessKeyId")
	private String accessKeyId;

	@ApiModelProperty(value = "AccessKey Secret")
	@JsonProperty("accessKeySecret")
	private String accessKeySecret;

	@ApiModelProperty(value = "安全令牌")
	@JsonProperty("securityToken")
	private String securityToken;

	@ApiModelProperty(value = "OSS访问域名")
	@JsonProperty("endpoint")
	private String endpoint;

	@ApiModelProperty(value = "Bucket名称")
	@JsonProperty("bucketName")
	private String bucketName;

	@ApiModelProperty(value = "文件存储路径前缀")
	@JsonProperty("filePrefix")
	private String filePrefix;

	@ApiModelProperty(value = "凭证过期时间（秒）")
	@JsonProperty("expiration")
	private Long expiration;
}
