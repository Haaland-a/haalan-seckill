package com.haalan.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

	/**
	 * 应用ID
	 */
	private String appId;

	/**
	 * 应用私钥
	 */
	private String privateKey;

	/**
	 * 支付宝公钥
	 */
	private String alipayPublicKey;

	/**
	 * 服务器地址
	 */
	private String serverUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

	/**
	 * 签名类型
	 */
	private String signType = "RSA2";

	/**
	 * 字符编码
	 */
	private String charset = "UTF-8";

	/**
	 * 数据格式
	 */
	private String format = "json";

	/**
	 * 异步通知地址
	 */
	private String notifyUrl;

	/**
	 * 同步返回地址
	 */
	private String returnUrl;


}
