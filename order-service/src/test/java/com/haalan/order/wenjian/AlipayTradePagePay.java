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
		String privateKey = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDFUY2sjCW0G4DOTWiazjboRCnNPFNzv9tezZJbnDcd/E31WEwuYlNrg21lvBJEpGFWfT/0pZFGCS1Y0VDaT2s/EgajGohXoTEPgE6LhGbavR2FSnist+6S90FlBAAyjhNjg3k4xmszVheqm2h23EznTEIFXC13LcxMq56iL6Ir59DLsd04fPxjCLVNXnThG3gOE152KLpEtZ3GUCLF42C8zFoNtHy1QV5D09IdIyZggIzbOd8/DNCkgtHgo0Q1xiNHHnR7JcoVLznW6wXk2hHXZVpC+PbjVva1pOw5mPGhxg+EIaTCNXf8JTgZIDgnpgcO/NJc+8Ww6fM8nYf2hVp/AgMBAAECggEABAgNh/f5ETv6nrxwJbGO56H05K+V2OiTl9VFZz/C1IcCJMAgFlaF881kn85+5Q4iuvymIN59IFwxo/3q0/sJaul4Xz68iGXPtcxeTnVvEIsKnl2OJ6E+a5xHpsO1KVNDzCW0hVdwe0UICMFSGhOYMqWZN7jRaPzzjqF3BUNJ4fG6WBvDYHVtB+pfg64/3jnC4IXeiRadnT61anIy7s0xW8p52mR3CsJdbHEQfQD+eJf4x066Nhjm9hHQCOtRpYXeEHeZmRtL2soH5cmU/QxcrbUfhNN7v/0/yxJKN9+46gtGUi2OypiKYHTSO+OQE6yvc6angBMgs2ALvNqktxHt4QKBgQD0QQRlUlGborjAZLw/rbzVVTgRs/3ZkrlactcG29hyuTDpBAytm5+9p6lsWhNVE0hF/iBMOpbpaX83RWJzScEwq99t8xwAMVC0M+HjTfrx3/iVQIkArujC5ssxIVK4j0FX8X7YOZ9rjZoMeaXKOjiYwEIyk1lurBGaSS4IBGnELwKBgQDOzrhF9O240se/v5LO0xUCQvFGdRcNE8/H+wblzt68HgXPUXst4t+6dRHwXBxYW9tJ3ohdItZxFQLRuD78u+7ZTkMHKTq9clNlvkim9jUFL9z0DSS3BcfpLwB8i5JbJfvBz3Tref4PTNtWUCyfZk2rbFfAV2AKcqKaKjvFNdsqsQKBgBgXUZesxt/S7OYNCYQ+XqGsBhu45RDQp/eD3DGQ/YhTO7+/5oA5GUhbgRpux/lcLYS5y/W40amz/hg1f8BfRe7HBQKTN+/M7WjYtS72QgoZTStrchffKJYQrXnrD32ko41JBHnPNSomjUsttGx90Zc/X0gdwHRqBFJzZVblGImBAoGBAK3ioo4vorIEiG9xZL8CPGfqYXQcf4U/YzWCnAoeJqmFlNz8ZdXvLK5aQPsYd7zTf05dDctBv4zVXcl4YX2fhvSwGSusPveLaRPjKrM30NzLobySUIyTD8PBAWe28xgwl74d8PENYdD7jnDEeMCOBEBemGnhDZ+7Q3zcheULo3wBAoGBAIGWmPn9mC4oNTELRat+D7HvI4SFZvbwoiFmKwLVjxzXnCn9TiVzHgax060arEtq/UcO7f5ESVXVGT74PLyNmH30I6cCXSmSffurYM+m1BV5DHwLVAUt/Jm8szH5OrdBRd6cgY08sxKMRoAD6WCk0bCHTYhTzGl7Y/Onk7SIYXvu";
		String alipayPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjBgHiN+LLVi+tE2tStTgDDoNVtaOf1mDSQZJZSNhEGpLfux65fLJikpoo8rIaBqNPv05uR40w/6AtLmW3k50g9czDhX8Wi9rMZhBTH2BP9NlHkkKeMC8F/yKWC6L9EljZHsMNSpw+23iuuRIIwX3med9jlzPW72kfvgDCKvbIeaY3oZ7H6qDWpSLKUFtnYQnh2BeMftYp0IzWcuIKm65ZbqCMEYQzJTEO9aGdeXu6djHFj3TVJXpHOXlmVjdgGv5st1v3ucyrWskqmXUScFr3zoug12kouOVXn7fdwA+ZnA0mlVoilJyHMckzxjB8k6/Gt+DlkO1TFIOxeCtoyW74wIDAQAB";
		AlipayConfig alipayConfig = new AlipayConfig();
		alipayConfig.setServerUrl("https://openapi-sandbox.dl.alipaydev.com/gateway.do");
		alipayConfig.setAppId("9021000163664721");
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