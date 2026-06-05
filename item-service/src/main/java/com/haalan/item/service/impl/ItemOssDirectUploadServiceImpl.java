package com.haalan.item.service.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import com.haalan.common.exception.CommonException;
import com.haalan.item.config.OssProperties;
import com.haalan.item.domain.vo.OssUploadCredentialVO;
import com.haalan.item.service.ItemOssDirectUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemOssDirectUploadServiceImpl implements ItemOssDirectUploadService {

	private final OssProperties ossProperties;

	/**
	 * <p>
	 * 获取OSS上传临时凭证（STS），路径为 /{spuCode}/
	 * </p>
	 */
	@Override
	public OssUploadCredentialVO getUploadCredential(String spuCode) {
		return buildCredential(spuCode, null);
	}

	/**
	 * <p>
	 * 获取OSS上传临时凭证（STS），路径为 /{spuCode}/{skuCode}/
	 * </p>
	 */
	@Override
	public OssUploadCredentialVO getUploadCredential(String spuCode, String skuCode) {
		return buildCredential(spuCode, skuCode);
	}

	private OssUploadCredentialVO buildCredential(String spuCode, String skuCode) {
		try {
			// 构建请求
			AssumeRoleRequest request = new AssumeRoleRequest();
			request.setSysMethod(MethodType.POST);
			// 设置角色ARN，需要在阿里云RAM控制台创建角色后获取
			request.setRoleArn(ossProperties.getRoleArn());
			request.setRoleSessionName("ItemOssUploadSession");
			request.setDurationSeconds(ossProperties.getStsDuration());

			// 构建资源路径: bucketName/{filePrefix}/{spuCode}/ 或 bucketName/{filePrefix}/{spuCode}/{skuCode}/
			String resourcePath;
			String uploadPrefix;
			if (skuCode != null) {
				resourcePath = String.format("acs:oss:*:*:%s/%s/%s/%s/*",
						ossProperties.getBucketName(), ossProperties.getFilePrefix(), spuCode, skuCode);
				uploadPrefix = String.format("%s/%s/%s", ossProperties.getFilePrefix(), spuCode, skuCode);
			} else {
				resourcePath = String.format("acs:oss:*:*:%s/%s/%s/*",
						ossProperties.getBucketName(), ossProperties.getFilePrefix(), spuCode);
				uploadPrefix = String.format("%s/%s", ossProperties.getFilePrefix(), spuCode);
			}

			String policy = String.format(
					"{\"Version\":\"1\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"oss:PutObject\",\"Resource\":\"%s\"}]}",
					resourcePath
			);
			request.setPolicy(policy);

			IClientProfile profile = DefaultProfile.getProfile(
					ossProperties.getRegionId(),
					ossProperties.getAccessKeyId(),
					ossProperties.getAccessKeySecret()
			);
			DefaultAcsClient client = new DefaultAcsClient(profile);

			AssumeRoleResponse response = client.getAcsResponse(request);
			AssumeRoleResponse.Credentials credentials = response.getCredentials();

			log.info("生成商品图片OSS上传凭证成功, 路径: {}", uploadPrefix);

			return OssUploadCredentialVO.builder()
					.accessKeyId(credentials.getAccessKeyId())
					.accessKeySecret(credentials.getAccessKeySecret())
					.securityToken(credentials.getSecurityToken())
					.endpoint(ossProperties.getEndpoint())
					.bucketName(ossProperties.getBucketName())
					.filePrefix(uploadPrefix)
					.expiration(ossProperties.getStsDuration())
					.regionId(ossProperties.getRegionId())
					.build();

		} catch (ClientException e) {
			log.error("获取OSS上传凭证失败", e);
			throw new CommonException("获取上传凭证失败: " + e.getMessage(), 500);
		}
	}
}
