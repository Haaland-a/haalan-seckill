package com.haalan.item.controller;

import com.haalan.common.domain.R;
import com.haalan.item.domain.vo.OssUploadCredentialVO;
import com.haalan.item.service.ItemOssDirectUploadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/oss")
@RequiredArgsConstructor
@Api(tags = "商品图片OSS直传管理")
public class ItemOssController {

	private final ItemOssDirectUploadService itemOssDirectUploadService;

	@GetMapping("/credential")
	@ApiOperation("获取OSS上传凭证（管理端上传商品图片）")
	public R<OssUploadCredentialVO> getOssUploadCredential(
			@RequestParam String spuCode,
			@RequestParam(required = false) String skuCode) {
		OssUploadCredentialVO credential;
		if (skuCode != null) {
			credential = itemOssDirectUploadService.getUploadCredential(spuCode, skuCode);
			log.info("管理端获取OSS上传凭证, spuCode: {}, skuCode: {}", spuCode, skuCode);
		} else {
			credential = itemOssDirectUploadService.getUploadCredential(spuCode);
			log.info("管理端获取OSS上传凭证, spuCode: {}", spuCode);
		}
		return R.success(credential);
	}
}
