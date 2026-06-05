package com.haalan.item.service;

import com.haalan.item.domain.vo.OssUploadCredentialVO;

public interface ItemOssDirectUploadService {

	/**
	 * 获取OSS上传临时凭证（STS）
	 *
	 * @param spuCode SPU编码，用于构建上传路径 /{spuCode}/
	 */
	OssUploadCredentialVO getUploadCredential(String spuCode);

	/**
	 * 获取OSS上传临时凭证（STS），带SKU路径
	 *
	 * @param spuCode SPU编码
	 * @param skuCode SKU编码，上传路径为 /{spuCode}/{skuCode}/
	 */
	OssUploadCredentialVO getUploadCredential(String spuCode, String skuCode);
}
