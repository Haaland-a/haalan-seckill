package com.haalan.order.controller;

import com.alipay.api.internal.util.AlipaySignature;
import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.order.config.AlipayProperties;
import com.haalan.order.domain.dto.PayRequestDTO;
import com.haalan.order.domain.vo.PayResponseVO;
import com.haalan.order.domain.vo.PayResultVO;
import com.haalan.order.service.ITPaymentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>
 * 支付表 前端控制器
 * </p>
 *
 * @author haaland
 * @since 2026-05-11
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Api(tags = "订单支付")
public class TPaymentController {

	private final ITPaymentService paymentService;

	@Resource
	private AlipayProperties alipayProperties;

	@PostMapping("/pay")
	@ApiOperation("创建支付订单")
	public R<PayResponseVO> createPayment(@RequestBody @Validated PayRequestDTO request) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 创建支付订单
		PayResponseVO response = paymentService.createPayment(userId, request);

		return R.success(response);
	}

	@PostMapping("/callback/alipay")
	@ApiOperation("支付宝支付回调")

	//外部回调的post接口我使用的是SakuraFrp进行端口映射，但是由于现在的政策原因，就支持https映射而且是野证书，
	// 所以异步回调没有被成功调用，但是支付宝会自动调用同步回调接口，
	// 所以支付成功后会跳转到支付成功页面，但是服务端无法得到回调信息去修改订单记录
	public String alipayCallback(HttpServletRequest request) throws Exception {
		log.info("收到支付宝异步通知");

		// 获取支付宝POST过来反馈信息
		Map<String, String> params = new HashMap<>();
		Map<String, String[]> requestParams = request.getParameterMap();
		for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext(); ) {
			String name = iter.next();
			String[] values = requestParams.get(name);
			String valueStr = "";
			for (int i = 0; i < values.length; i++) {
				valueStr = (i == values.length - 1) ? valueStr + values[i]
						: valueStr + values[i] + ",";
			}
			// 乱码解决，这段代码在出现乱码时使用
			valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
			params.put(name, valueStr);
		}

		log.info("支付宝回调参数: {}", params);

		boolean signVerified = AlipaySignature.rsaCheckV1(
				params,
				alipayProperties.getAlipayPublicKey(),
				alipayProperties.getCharset(),
				alipayProperties.getSignType()
		);

		if (signVerified) {
			// 商户订单号
			String outTradeNo = new String(request.getParameter("out_trade_no").getBytes("ISO-8859-1"), "UTF-8");
			// 支付宝交易号
			String tradeNo = new String(request.getParameter("trade_no").getBytes("ISO-8859-1"), "UTF-8");
			// 交易状态
			String tradeStatus = new String(request.getParameter("trade_status").getBytes("ISO-8859-1"), "UTF-8");
			// 付款金额
			String totalAmount = new String(request.getParameter("total_amount").getBytes("ISO-8859-1"), "UTF-8");

			log.info("验签成功 - 订单号: {}, 支付宝交易号: {}, 状态: {}, 金额: {}",
					outTradeNo, tradeNo, tradeStatus, totalAmount);

			if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
				// 处理支付成功逻辑
				String result = paymentService.handleAlipayCallback(outTradeNo, tradeNo, totalAmount);
				return result;
			}

			log.info("交易状态: {}", tradeStatus);
			return "success";
		} else {
			log.error("支付宝回调验签失败");
			return "fail";
		}
	}

	@GetMapping("/result/{orderNo}")
	@ApiOperation("查询支付结果")
	public R<PayResultVO> getPayResult(@PathVariable String orderNo) {
		// 从上下文获取当前用户ID
		Long userId = UserContext.getUser();

		// 查询支付结果
		PayResultVO result = paymentService.getPayResult(orderNo, userId);

		return R.success(result);
	}
}
