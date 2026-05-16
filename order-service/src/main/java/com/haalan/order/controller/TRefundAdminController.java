package com.haalan.order.controller;

import com.haalan.common.domain.PageResult;
import com.haalan.common.domain.R;
import com.haalan.order.domain.dto.RefundAuditRequestDTO;
import com.haalan.order.domain.vo.RefundListItemVO;
import com.haalan.order.service.ITRefundService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *
 * @author Haaland
 * @description TRefundAdminController
 * </p>
 * @date 2026/5/16
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/payment")
@RequiredArgsConstructor
@Api(tags = "退款管理")
public class TRefundAdminController {
	private final ITRefundService refundService;

	@PostMapping("/refund")
	@ApiOperation("审核退款（管理端）")
	public R<Void> auditRefund(@RequestBody @Validated RefundAuditRequestDTO request) {
		// 审核退款
		refundService.auditRefund(request);

		return R.success("退款完成");
	}

	@GetMapping("/refund/list")
	@ApiOperation("获取退款列表（管理端）")
	public R<PageResult<RefundListItemVO>> getRefundList(
			@RequestParam(defaultValue = "1") Integer pageNum,
			@RequestParam(defaultValue = "10") Integer pageSize,
			@RequestParam(required = false) Integer status) {

		// 查询退款列表
		PageResult<RefundListItemVO> refundList = refundService.getRefundList(pageNum, pageSize, status);

		return R.success(refundList);
	}
}
