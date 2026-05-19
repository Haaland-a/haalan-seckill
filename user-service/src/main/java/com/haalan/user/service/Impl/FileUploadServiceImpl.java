package com.haalan.user.service.Impl;

import com.aliyun.oss.OSS;
import com.haalan.common.exception.CommonException;
import com.haalan.user.config.OssProperties;
import com.haalan.user.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

	private final OSS ossClient;
	private final OssProperties ossProperties;

	/**
	 * <p>
	 * 上传头像到阿里云OSS
	 * </p>
	 *
	 * @param file 头像文件
	 * @return 上传后的文件URL
	 * @author Haaland
	 * @date 2026/5/19
	 */
	@Override
	public String uploadAvatar(MultipartFile file) {
		// 验证文件
		if (file == null || file.isEmpty()) {
			throw new CommonException("上传文件不能为空", 400);
		}

		// 验证文件大小（限制为5MB）
		long maxSize = 5 * 1024 * 1024;
		if (file.getSize() > maxSize) {
			throw new CommonException("文件大小不能超过5MB", 400);
		}

		// 验证文件类型
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null || !isImageFile(originalFilename)) {
			throw new CommonException("只支持图片格式（jpg、jpeg、png、gif）", 400);
		}

		try {
			// 生成文件名：日期/UUID.扩展名
			String extension = getFileExtension(originalFilename);
			String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
			String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
			String objectName = ossProperties.getFilePrefix() + "/" + datePath + "/" + fileName;

			// 上传到OSS
			ossClient.putObject(ossProperties.getBucketName(), objectName, file.getInputStream());

			// 构建访问URL
			String url = "https://" + ossProperties.getBucketName() + "." +
					ossProperties.getEndpoint() + "/" + objectName;

			log.info("头像上传成功，URL: {}", url);
			return url;

		} catch (IOException e) {
			log.error("头像上传失败", e);
			throw new CommonException("头像上传失败: " + e.getMessage(), 500);
		}
	}

	/**
	 * 判断是否为图片文件
	 */
	private boolean isImageFile(String filename) {
		String lowerCase = filename.toLowerCase();
		return lowerCase.endsWith(".jpg") ||
				lowerCase.endsWith(".jpeg") ||
				lowerCase.endsWith(".png") ||
				lowerCase.endsWith(".gif");
	}

	/**
	 * 获取文件扩展名
	 */
	private String getFileExtension(String filename) {
		int lastIndexOf = filename.lastIndexOf(".");
		if (lastIndexOf == -1) {
			return "";
		}
		return filename.substring(lastIndexOf + 1).toLowerCase();
	}
}
