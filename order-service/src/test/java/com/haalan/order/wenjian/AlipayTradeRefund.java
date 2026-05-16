package com.haalan.order.wenjian;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.OpenApiRoyaltyDetailInfoPojo;
import com.alipay.api.domain.RefundGoodsDetail;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;

import java.util.ArrayList;
import java.util.List;

public class AlipayTradeRefund {

	public static void main(String[] args) throws AlipayApiException {
		// 初始化SDK
		AlipayClient alipayClient = new DefaultAlipayClient(getAlipayConfig());

		// 构造请求参数以调用接口
		AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
		AlipayTradeRefundModel model = new AlipayTradeRefundModel();

		// 设置商户订单号
		model.setOutTradeNo("20150320010101001");

		// 设置支付宝交易号
		model.setTradeNo("2014112611001004680073956707");

		// 设置退款金额
		model.setRefundAmount("200.12");

		// 设置退款原因说明
		model.setRefundReason("正常退款");

		// 设置退款请求号
		model.setOutRequestNo("HZ01RF001");

		// 设置退款包含的商品列表信息
		List<RefundGoodsDetail> refundGoodsDetail = new ArrayList<RefundGoodsDetail>();
		RefundGoodsDetail refundGoodsDetail0 = new RefundGoodsDetail();
		refundGoodsDetail0.setOutSkuId("outSku_01");
		refundGoodsDetail0.setOutItemId("outItem_01");
		refundGoodsDetail0.setGoodsId("apple-01");
		refundGoodsDetail0.setRefundAmount("19.50");
		List<String> outCertificateNoList = new ArrayList<String>();
		outCertificateNoList.add("202407013232143241231243243423");
		refundGoodsDetail0.setOutCertificateNoList(outCertificateNoList);
		refundGoodsDetail.add(refundGoodsDetail0);
		model.setRefundGoodsDetail(refundGoodsDetail);

		// 设置退分账明细信息
		List<OpenApiRoyaltyDetailInfoPojo> refundRoyaltyParameters = new ArrayList<OpenApiRoyaltyDetailInfoPojo>();
		OpenApiRoyaltyDetailInfoPojo refundRoyaltyParameters0 = new OpenApiRoyaltyDetailInfoPojo();
		refundRoyaltyParameters0.setAmount("0.1");
		refundRoyaltyParameters0.setTransIn("2088101126708402");
		refundRoyaltyParameters0.setRoyaltyType("transfer");
		refundRoyaltyParameters0.setTransOut("2088101126765726");
		refundRoyaltyParameters0.setTransOutType("userId");
		refundRoyaltyParameters0.setRoyaltyScene("达人佣金");
		refundRoyaltyParameters0.setTransInType("userId");
		refundRoyaltyParameters0.setTransInName("张三");
		refundRoyaltyParameters0.setDesc("分账给2088101126708402");
		refundRoyaltyParameters.add(refundRoyaltyParameters0);
		model.setRefundRoyaltyParameters(refundRoyaltyParameters);

		// 设置查询选项
		List<String> queryOptions = new ArrayList<String>();
		queryOptions.add("refund_detail_item_list");
		model.setQueryOptions(queryOptions);

		// 设置针对账期交易
		model.setRelatedSettleConfirmNo("2024041122001495000530302869");

		request.setBizModel(model);
		// 第三方代调用模式下请设置app_auth_token
		// request.putOtherTextParam("app_auth_token", "<-- 请填写应用授权令牌 -->");

		AlipayTradeRefundResponse response = alipayClient.execute(request);
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