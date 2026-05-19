package com.haalan.user.service.Impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import com.haalan.common.exception.CommonException;
import com.haalan.user.config.OssProperties;
import com.haalan.user.domain.vo.OssUploadCredentialVO;
import com.haalan.user.service.OssDirectUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OSS直传服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OssDirectUploadServiceImpl implements OssDirectUploadService {

	private final OssProperties ossProperties;

	/**
	 * <p>
	 * 获取OSS上传临时凭证（STS）
	 * </p>
	 *
	 * @return 上传凭证
	 * @author Haaland
	 * @date 2026/5/19
	 */
	@Override
	public OssUploadCredentialVO getUploadCredential() {
		try {
			// 构建请求
			AssumeRoleRequest request = new AssumeRoleRequest();
			request.setSysMethod(MethodType.POST);
			// 设置角色ARN，需要在阿里云RAM控制台创建角色后获取
			// 格式：acs:ram::${AccountId}:role/${RoleName}
			request.setRoleArn(ossProperties.getRoleArn());
			request.setRoleSessionName("OssUploadSession");
			request.setDurationSeconds(ossProperties.getStsDuration());

			// 设置策略，限制只能上传到指定目录
			String policy = String.format(
					"{\"Version\":\"1\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"oss:PutObject\",\"Resource\":\"acs:oss:*:*:%s/%s/*\"}]}",
					ossProperties.getBucketName(),
					ossProperties.getFilePrefix()
			);
			request.setPolicy(policy);

			// 创建客户端
			IClientProfile profile = DefaultProfile.getProfile(
					ossProperties.getRegionId(),
					ossProperties.getAccessKeyId(),
					ossProperties.getAccessKeySecret()
			);
			DefaultAcsClient client = new DefaultAcsClient(profile);

			// 获取临时凭证
			AssumeRoleResponse response = client.getAcsResponse(request);
			AssumeRoleResponse.Credentials credentials = response.getCredentials();

			log.info("生成OSS上传凭证成功，过期时间: {}秒", ossProperties.getStsDuration());

			return OssUploadCredentialVO.builder()
					.accessKeyId(credentials.getAccessKeyId())
					.accessKeySecret(credentials.getAccessKeySecret())
					.securityToken(credentials.getSecurityToken())
					.endpoint(ossProperties.getEndpoint())
					.bucketName(ossProperties.getBucketName())
					.filePrefix(ossProperties.getFilePrefix())
					.expiration(ossProperties.getStsDuration())
					.build();

		} catch (ClientException e) {
			log.error("获取OSS上传凭证失败", e);
			throw new CommonException("获取上传凭证失败: " + e.getMessage(), 500);
		}
	}
}
