package com.haalan.order.wenjian;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;

import java.util.ArrayList;
import java.util.List;

public class AlipayTradeQuery {

	public static void main(String[] args) throws AlipayApiException {
		// 初始化SDK
		AlipayClient alipayClient = new DefaultAlipayClient(getAlipayConfig());

		// 构造请求参数以调用接口
		AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
		AlipayTradeQueryModel model = new AlipayTradeQueryModel();

		// 设置订单支付时传入的商户订单号
		model.setOutTradeNo("20150320010101001");

		// 设置支付宝交易号
		model.setTradeNo("2014112611001004680 073956707");

		// 设置查询选项
		List<String> queryOptions = new ArrayList<String>();
		queryOptions.add("trade_settle_info");
		model.setQueryOptions(queryOptions);

		request.setBizModel(model);
		// 第三方代调用模式下请设置app_auth_token
		// request.putOtherTextParam("app_auth_token", "<-- 请填写应用授权令牌 -->");

		AlipayTradeQueryResponse response = alipayClient.execute(request);
		System.out.println(response.getBody());

		if (response.isSuccess()) {
			System.out.println("调用成功");
		} else {
			System.out.println("调用失败");
			// sdk版本是"4.38.0.ALL"及以上,可以参考下面的示例获取诊断链接
			// String diagnosisUrl = DiagnosisUtils.getDiagnosisUrl(response);
			// System.out.println(diagnosisUrl);
		}
	}

	private static AlipayConfig getAlipayConfig() {
		String privateKey = "<-- 请填写您的应用私钥，例如：MIIEvQIBADANB ... ... -->";
		String alipayPublicKey = "<-- 请填写您的支付宝公钥，例如：MIIBIjANBg... -->";
		AlipayConfig alipayConfig = new AlipayConfig();
		alipayConfig.setServerUrl("https://openapi.alipay.com/gateway.do");
		alipayConfig.setAppId("<-- 请填写您的AppId，例如：2019091767145019 -->");
		alipayConfig.setPrivateKey(privateKey);
		alipayConfig.setFormat("json");
		alipayConfig.setAlipayPublicKey(alipayPublicKey);
		alipayConfig.setCharset("UTF-8");
		alipayConfig.setSignType("RSA2");
		return alipayConfig;
	}
}