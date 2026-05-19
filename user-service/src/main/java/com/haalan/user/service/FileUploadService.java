package com.haalan.user.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务接口
 */
public interface FileUploadService {

	/**
	 * 上传头像到阿里云OSS
	 *
	 * @param file 头像文件
	 * @return 上传后的文件URL
	 */
	String uploadAvatar(MultipartFile file);
}
