package com.haalan.order.wenjian;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AlipayTradePagePay {

	public static void main(String[] args) throws AlipayApiException {
		// ⚠️ 安全：此文件曾被提交进 git，真实支付宝私钥已泄露（提交 169b5b1 / 729489a / eca8f7b / e02d26b）。
		// 现已改为从 Nacos 的 share-pay.yaml 读取（alipay.private-key / alipay.alipay-public-key / alipay.app-id），
		// 严禁再把真实密钥写回本文件。本地调试前请先去 Nacos 控制台复制后手工填入下面三行（不要提交）。
		String privateKey = System.getProperty("alipay.privateKey", "<已脱敏-请从 Nacos share-pay.yaml 的 alipay.private-key 读取>");
		String alipayPublicKey = System.getProperty("alipay.publicKey", "<已脱敏-请从 Nacos share-pay.yaml 的 alipay.alipay-public-key 读取>");
		AlipayConfig alipayConfig = new AlipayConfig();
		alipayConfig.setServerUrl("https://openapi-sandbox.dl.alipaydev.com/gateway.do");
		alipayConfig.setAppId(System.getProperty("alipay.appId", "<已脱敏-请从 Nacos share-pay.yaml 的 alipay.app-id 读取>"));
		alipayConfig.setPrivateKey(privateKey);
		alipayConfig.setFormat("json");
		alipayConfig.setAlipayPublicKey(alipayPublicKey);
		alipayConfig.setCharset("UTF-8");
		alipayConfig.setSignType("RSA2");
		AlipayClient alipayClient = new DefaultAlipayClient(alipayConfig);
		AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
		AlipayTradePagePayModel model = new AlipayTradePagePayModel();
		model.setOutTradeNo("201503200101012001");
		model.setTotalAmount("88.88");
		model.setSubject("Iphone6 16G");
		model.setProductCode("FAST_INSTANT_TRADE_PAY");

		// 设置PC扫码支付的方式
		model.setQrPayMode("1");

//		// 设置订单包含的商品列表信息
//		List<GoodsDetail> goodsDetail = new ArrayList<GoodsDetail>();
//		GoodsDetail goodsDetail0 = new GoodsDetail();
//		goodsDetail0.setOutSkuId("outSku_01");
//		goodsDetail0.setGoodsName("ipad");
//		goodsDetail0.setAlipayGoodsId("20010001");
//		goodsDetail0.setQuantity(1L);
//		goodsDetail0.setPrice("2000");
//		goodsDetail0.setOutItemId("outItem_01");
//		goodsDetail0.setGoodsId("apple-01");
//		goodsDetail0.setGoodsCategory("34543238");
//		goodsDetail0.setCategoriesTree("124868003|126232002|126252004");
//		goodsDetail0.setShowUrl("http://www.alipay.com/xxx.jpg");
//		goodsDetail.add(goodsDetail0);
//		model.setGoodsDetail(goodsDetail);

		// 推荐：相对超时时间（单位：分钟）
		model.setTimeoutExpress("30m");

		request.setBizModel(model);
		AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "POST");
		// 如果需要返回GET请求，请使用
		// AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "GET");
		String pageRedirectionData = response.getBody();
		System.out.println(pageRedirectionData);
		if (response.isSuccess()) {
			log.error("支付宝返回: code={}, msg={}, subCode={}, subMsg={}, body={}",
					response.getCode(),
					response.getMsg(),
					response.getSubCode(),
					response.getSubMsg(),
					response.getBody());
			System.out.println("调用成功");
		} else {
			System.out.println("调用失败");
			// sdk版本是"4.38.0.ALL"及以上,可以参考下面的示例获取诊断链接
			// String diagnosisUrl = DiagnosisUtils.getDiagnosisUrl(response);
			// System.out.println(diagnosisUrl);
		}
	}
}