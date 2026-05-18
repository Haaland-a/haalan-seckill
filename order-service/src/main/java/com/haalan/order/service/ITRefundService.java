package com.haalan.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.common.domain.PageResult;
import com.haalan.order.domain.dto.RefundAuditRequestDTO;
import com.haalan.order.domain.dto.RefundRequestDTO;
import com.haalan.order.domain.po.TRefund;
import com.haalan.order.domain.vo.RefundListItemVO;
import com.haalan.order.domain.vo.RefundResponseVO;

/**
 * <p>
 * 退款申请表 服务类
 * </p>
 *
 * @author haaland
 * @since 2026-05-16
 */
public interface ITRefundService extends IService<TRefund> {

	/**
	 * 申请退款
	 *
	 * @param userId  用户ID
	 * @param request 退款申请请求
	 * @return 退款响应
	 */
	RefundResponseVO applyRefund(Long userId, RefundRequestDTO request);

	/**
	 * 审核退款（管理端）
	 *
	 * @param request 退款审核请求
	 */
	void auditRefund(RefundAuditRequestDTO request);

	/**
	 * 获取退款列表（管理端）
	 *
	 * @param pageNum  页码
	 * @param pageSize 每页大小
	 * @param status   状态筛选（可选）
	 * @return 退款列表分页结果
	 */
	PageResult<RefundListItemVO> getRefundList(Integer pageNum, Integer pageSize, Integer status);
}
