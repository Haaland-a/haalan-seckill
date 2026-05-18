package com.haalan.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加密工具类
 * 用于敏感ID的加密传输
 */
@Slf4j
public class EncryptUtils {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_IV_LENGTH = 12; // IV长度
	private static final int GCM_TAG_LENGTH = 128; // 认证标签长度

	/**
	 * 默认密钥（生产环境应该从配置中心获取）
	 * 注意：这里使用固定密钥仅用于演示，生产环境应该使用更安全的密钥管理方式
	 */
	private static final String DEFAULT_KEY = "HaalanEncryptKey2026SecureKey!!!"; // 32字节

	/**
	 * 加密Long类型的ID
	 *
	 * @param id 要加密的ID
	 * @return 加密后的字符串（Base64编码）
	 */
	public static String encryptId(Long id) {
		if (id == null) {
			return null;
		}
		return encrypt(id.toString());
	}

	/**
	 * 解密为Long类型的ID
	 *
	 * @param encryptedText 加密的字符串
	 * @return 解密后的Long ID
	 */
	public static Long decryptToLong(String encryptedText) {
		if (encryptedText == null || encryptedText.isEmpty()) {
			return null;
		}
		String decrypted = decrypt(encryptedText);
		try {
			return Long.parseLong(decrypted);
		} catch (NumberFormatException e) {
			log.error("解密后的字符串无法转换为Long: {}", decrypted, e);
			throw new IllegalArgumentException("解密失败");
		}
	}

	/**
	 * 加密字符串
	 *
	 * @param plaintext 明文
	 * @return 密文（Base64编码）
	 */
	public static String encrypt(String plaintext) {
		if (plaintext == null || plaintext.isEmpty()) {
			return plaintext;
		}

		try {
			// 生成随机IV
			byte[] iv = new byte[GCM_IV_LENGTH];
			SecureRandom random = new SecureRandom();
			random.nextBytes(iv);

			// 初始化Cipher
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			SecretKeySpec keySpec = new SecretKeySpec(DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

			// 加密
			byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
			byte[] ciphertext = cipher.doFinal(plaintextBytes);

			// 将IV和密文组合在一起
			ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
			byteBuffer.put(iv);
			byteBuffer.put(ciphertext);

			// Base64编码
			return Base64.getEncoder().encodeToString(byteBuffer.array());

		} catch (Exception e) {
			log.error("加密失败", e);
			throw new RuntimeException("加密失败", e);
		}
	}

	/**
	 * 解密字符串
	 *
	 * @param encryptedText 密文（Base64编码）
	 * @return 明文
	 */
	public static String decrypt(String encryptedText) {
		if (encryptedText == null || encryptedText.isEmpty()) {
			return encryptedText;
		}

		try {
			// Base64解码
			byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

			// 提取IV和密文
			ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
			byte[] iv = new byte[GCM_IV_LENGTH];
			byteBuffer.get(iv);
			byte[] ciphertext = new byte[byteBuffer.remaining()];
			byteBuffer.get(ciphertext);

			// 初始化Cipher
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			SecretKeySpec keySpec = new SecretKeySpec(DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

			// 解密
			byte[] plaintext = cipher.doFinal(ciphertext);

			return new String(plaintext, StandardCharsets.UTF_8);

		} catch (Exception e) {
			log.error("解密失败", e);
			throw new IllegalArgumentException("解密失败，数据可能已被篡改");
		}
	}
}
