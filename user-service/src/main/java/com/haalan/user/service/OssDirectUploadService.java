package com.haalan.user.service;

import com.haalan.user.domain.vo.OssUploadCredentialVO;

/**
 * OSS直传服务接口
 */
public interface OssDirectUploadService {

	/**
	 * 获取OSS上传临时凭证
	 *
	 * @return 上传凭证
	 */
	OssUploadCredentialVO getUploadCredential();
}
