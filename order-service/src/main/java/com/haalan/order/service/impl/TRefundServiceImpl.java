package com.haalan.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.domain.PageResult;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.order.config.AlipayProperties;
import com.haalan.order.domain.dto.RefundAuditRequestDTO;
import com.haalan.order.domain.dto.RefundRequestDTO;
import com.haalan.order.domain.po.TPayment;
import com.haalan.order.domain.po.TRefund;
import com.haalan.order.domain.po.TSeckillOrder;
import com.haalan.order.domain.vo.RefundListItemVO;
import com.haalan.order.domain.vo.RefundResponseVO;
import com.haalan.order.mapper.TRefundMapper;
import com.haalan.order.mapper.TSeckillOrder0Mapper;
import com.haalan.order.mapper.TSeckillOrder1Mapper;
import com.haalan.order.service.ITRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 退款申请表 服务实现类
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TRefundServiceImpl extends ServiceImpl<TRefundMapper, TRefund> implements ITRefundService {

	private final TSeckillOrder0Mapper seckillOrder0Mapper;
	private final TSeckillOrder1Mapper seckillOrder1Mapper;

	@Resource
	private AlipayProperties alipayProperties;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public RefundResponseVO applyRefund(Long userId, RefundRequestDTO request) {
		String orderNo = request.getOrderNo();
		BigDecimal refundAmount = request.getRefundAmount();

		log.info("用户申请退款, userId={}, orderNo={}, refundAmount={}", userId, orderNo, refundAmount);

		// 1. 查询订单信息
		TSeckillOrder order = selectOrder(userId, orderNo);


		// 2. 验证订单状态（只有已支付的订单才能退款）
		if (order.getStatus() != 1) {
			throw new BizIllegalException("订单状态异常，无法申请退款");
		}

		// 3. 验证用户权限
		if (!order.getUserId().equals(userId)) {
			throw new BizIllegalException("无权操作此订单");
		}

		// 4. 验证退款金额
		if (refundAmount.compareTo(order.getTotalAmount()) > 0) {
			throw new BizIllegalException("退款金额不能超过订单金额");
		}

		// 5. 检查是否已经有处理中的退款申请
		Long existingRefundCount = this.lambdaQuery()
				.eq(TRefund::getOrderNo, orderNo)
				.in(TRefund::getStatus, 0, 1) // 处理中或退款成功
				.count();
		if (existingRefundCount > 0) {
			throw new BizIllegalException("该订单已有退款申请，请勿重复提交");
		}

		// 6. 查询支付记录获取支付宝交易号
		TPayment payment = new com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<com.haalan.order.mapper.TPaymentMapper, TPayment>() {
		}.lambdaQuery()
				.eq(TPayment::getOrderNo, orderNo)
				.one();

		if (payment == null || payment.getTransactionId() == null) {
			throw new BizIllegalException("支付记录不存在，无法退款");
		}

		// 7. 创建退款申请记录
		String refundNo = generateRefundNo();
		TRefund refund = new TRefund();
		refund.setRefundNo(refundNo);
		refund.setOrderNo(orderNo);
		refund.setUserId(userId);
		refund.setRefundAmount(refundAmount);
		refund.setRefundReason(request.getRefundReason());
		refund.setStatus(0); // 处理中
		refund.setTradeNo(payment.getTransactionId());
		refund.setApplyTime(LocalDateTime.now());
		this.save(refund);

		log.info("退款申请创建成功, refundNo={}, orderNo={}", refundNo, orderNo);

		// 8. 构建响应
		return RefundResponseVO.builder()
				.refundNo(refundNo)
				.orderNo(orderNo)
				.refundAmount(refundAmount)
				.status(0)
				.statusName("处理中")
				.build();
	}

	private TSeckillOrder selectOrder(Long userId, String orderNo) {
		int tableSuffix = (int) (userId % 2);
		TSeckillOrder order;
		if (tableSuffix == 0) {
			order = seckillOrder0Mapper.selectOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
			);
		} else {
			order = seckillOrder1Mapper.selectOne(
					new LambdaQueryWrapper<TSeckillOrder>()
							.eq(TSeckillOrder::getOrderNo, orderNo)
			);
		}

		if (order == null) {
			throw new BizIllegalException("订单不存在");
		}
		return order;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void auditRefund(RefundAuditRequestDTO request) {
		String refundNo = request.getRefundNo();
		Boolean approved = request.getApproved();

		log.info("管理端审核退款, refundNo={}, approved={}", refundNo, approved);

		// 1. 查询退款记录
		TRefund refund = this.lambdaQuery()
				.eq(TRefund::getRefundNo, refundNo)
				.one();

		if (refund == null) {
			throw new BizIllegalException("退款记录不存在");
		}

		// 2. 验证退款状态（只有处理中的才能审核）
		if (refund.getStatus() != 0) {
			throw new BizIllegalException("退款状态异常，无法退款");
		}

		if (approved) {
			// 3. 通过退款 - 调用支付宝退款接口
			executeAlipayRefund(refund);
		} else {
			// 4. 拒绝退款
			if (request.getRejectReason() == null || request.getRejectReason().trim().isEmpty()) {
				throw new BizIllegalException("拒绝退款时必须填写拒绝原因");
			}
			refund.setStatus(3); // 已拒绝
			refund.setRejectReason(request.getRejectReason());
			refund.setHandleTime(LocalDateTime.now());
			this.updateById(refund);
			log.info("退款申请已拒绝, refundNo={}, reason={}", refundNo, request.getRejectReason());
		}
	}

	private AlipayClient initAlipayClient() throws AlipayApiException {
		AlipayConfig config = new AlipayConfig();

		config.setServerUrl(alipayProperties.getServerUrl());
		config.setAppId(alipayProperties.getAppId());
		config.setPrivateKey(alipayProperties.getPrivateKey());
		config.setFormat("json");
		config.setCharset("UTF-8");
		config.setAlipayPublicKey(alipayProperties.getAlipayPublicKey());
		config.setSignType("RSA2");
		return new DefaultAlipayClient(config);
	}

	/**
	 * 执行支付宝退款
	 */
	private void executeAlipayRefund(TRefund refund) {
		try {
			log.info("开始调用支付宝退款接口, refundNo={}, tradeNo={}", refund.getRefundNo(), refund.getTradeNo());

			// 1. 初始化支付宝客户端

			AlipayClient alipayClient = initAlipayClient();

			// 2. 构造退款请求
			AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
			AlipayTradeRefundModel model = new AlipayTradeRefundModel();
			model.setOutTradeNo(refund.getOrderNo()); // 商户订单号
			model.setTradeNo(refund.getTradeNo()); // 支付宝交易号
			model.setRefundAmount(refund.getRefundAmount().toString()); // 退款金额
			model.setRefundReason(refund.getRefundReason()); // 退款原因
			model.setOutRequestNo(refund.getRefundNo()); // 退款请求号（唯一）
			request.setBizModel(model);

			// 3. 执行退款
			AlipayTradeRefundResponse response = alipayClient.execute(request);
			log.info("支付宝退款响应: {}", response.getBody());

			if (response.isSuccess()) {
				// 4. 退款成功
				refund.setStatus(1); // 退款成功
				refund.setRefundTradeNo(response.getTradeNo()); // 支付宝退款流水号
				refund.setHandleTime(LocalDateTime.now());
				refund.setCompleteTime(LocalDateTime.now());
				this.updateById(refund);

				// 5. 更新订单状态为已取消
				updateOrderStatusToCancelled(refund.getOrderNo(), refund.getUserId());

				log.info("支付宝退款成功, refundNo={}, refundTradeNo={}", refund.getRefundNo(), response.getTradeNo());
			} else {
				// 退款失败
				refund.setStatus(2); // 退款失败
				refund.setHandleTime(LocalDateTime.now());
				this.updateById(refund);
				log.error("支付宝退款失败, refundNo={}, subCode={}, subMsg={}",
						refund.getRefundNo(), response.getSubCode(), response.getSubMsg());
				throw new BizIllegalException("支付宝退款失败: " + response.getSubMsg());
			}
		} catch (AlipayApiException e) {
			log.error("调用支付宝退款API异常, refundNo={}", refund.getRefundNo(), e);
			throw new BizIllegalException("支付宝退款失败");
		}
	}

	/**
	 * 更新订单状态为已取消
	 */
	private void updateOrderStatusToCancelled(String orderNo, Long userId) {
		int tableSuffix = (int) (userId % 2);
		TSeckillOrder order = selectOrder(userId, orderNo);

		if (order != null && order.getStatus() == 1) {
			// 只有已支付的订单才更新为已取消
			order.setStatus(2); // 已取消
			order.setCancelTime(LocalDateTime.now());
			if (tableSuffix == 0) {
				seckillOrder0Mapper.updateById(order);
			} else {
				seckillOrder1Mapper.updateById(order);
			}
			log.info("订单状态更新为已取消, orderNo={}", orderNo);
		}
	}

	@Override
	public PageResult<RefundListItemVO> getRefundList(Integer pageNum, Integer pageSize, Integer status) {
		// 1. 构建分页对象
		Page<TRefund> page = new Page<>(pageNum, pageSize);

		// 2. 构建查询条件
		LambdaQueryWrapper<TRefund> queryWrapper = new LambdaQueryWrapper<>();
		if (status != null) {
			queryWrapper.eq(TRefund::getStatus, status);
		}
		queryWrapper.orderByDesc(TRefund::getApplyTime);

		// 3. 执行分页查询
		Page<TRefund> refundPage = this.page(page, queryWrapper);

		// 4. 转换为VO
		List<RefundListItemVO> voList = refundPage.getRecords().stream()
				.map(this::convertToVO)
				.collect(Collectors.toList());

		// 5. 构建分页结果
		PageResult<RefundListItemVO> result = new PageResult<>();
		result.setTotal(refundPage.getTotal());
		result.setPageNum((int) refundPage.getCurrent());
		result.setPageSize((int) refundPage.getSize());
		result.setList(voList);

		return result;
	}

	/**
	 * 转换为VO
	 */
	private RefundListItemVO convertToVO(TRefund refund) {
		RefundListItemVO vo = new RefundListItemVO();
		vo.setRefundNo(refund.getRefundNo());
		vo.setOrderNo(refund.getOrderNo());
		vo.setUserId(refund.getUserId());
		vo.setRefundAmount(refund.getRefundAmount());
		vo.setRefundReason(refund.getRefundReason());
		vo.setStatus(refund.getStatus());
		vo.setStatusName(getRefundStatusName(refund.getStatus()));
		vo.setRejectReason(refund.getRejectReason());
		vo.setApplyTime(refund.getApplyTime());
		vo.setHandleTime(refund.getHandleTime());
		vo.setCompleteTime(refund.getCompleteTime());
		return vo;
	}

	/**
	 * 生成退款单号
	 */
	private String generateRefundNo() {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String random = IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
		return "RF" + timestamp + random;
	}

	/**
	 * 获取退款状态名称
	 */
	private String getRefundStatusName(Integer status) {
		if (status == null) {
			return "未知";
		}
		return switch (status) {
			case 0 -> "处理中";
			case 1 -> "退款成功";
			case 2 -> "退款失败";
			case 3 -> "已拒绝";
			default -> "未知";
		};
	}
}
