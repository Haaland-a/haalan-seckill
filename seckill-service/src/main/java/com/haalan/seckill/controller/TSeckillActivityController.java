package com.haalan.seckill.controller;


import com.haalan.common.domain.R;
import com.haalan.seckill.domain.dto.*;
import com.haalan.seckill.domain.vo.*;
import com.haalan.seckill.service.ITSeckillActivityService;
import com.haalan.seckill.service.ITSeckillPreheatService;
import com.haalan.seckill.service.ITSeckillProductService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 秒杀活动表 前端控制器
 * </p>
 *
 * @author lyc
 * @since 2026-04-23
 */
@RestController
@RequestMapping("/api/admin/seckill")
@RequiredArgsConstructor
@Api(tags = "秒杀活动管理（管理端）")
public class TSeckillActivityController {

	private final ITSeckillActivityService seckillActivityService;
	private final ITSeckillProductService seckillProductService;
	private final ITSeckillPreheatService preheatService;

	@PostMapping("/activity")
	@ApiOperation("创建秒杀活动")
	public R<SeckillActivityCreateResultVO> createActivity(@RequestBody @Validated SeckillActivityCreateDTO dto) {
		return R.success("创建成功", seckillActivityService.createActivity(dto));
	}

	@PutMapping("/activity/{activityId}")
	@ApiOperation("修改秒杀活动")
	public R<Void> updateActivity(@PathVariable("activityId") Long activityId, @RequestBody @Validated SeckillActivityUpdateDTO dto) {
		seckillActivityService.updateActivity(activityId, dto);
		return R.success("修改成功", null);
	}

	//管理端回显秒杀活动
	@GetMapping("/activity/{id}")
	@ApiOperation("回显秒杀活动")
	public R<SeckillActivityUpdateDTO> echoActivity(@PathVariable Long id) {
		return R.success(seckillActivityService.echoActivity(id));
	}

	@PostMapping("/activity/{activityId}/product")
	@ApiOperation("添加秒杀商品")
	public R<SeckillActivityAddPVO> addProduct(@PathVariable("activityId") Long activityId, @RequestBody @Validated SeckillActivityAddPDTO dto) {
		SeckillActivityAddPVO result = seckillProductService.addProduct(activityId, dto);
		return R.success("添加成功", result);
	}

	@PostMapping("/activity/{activityId}/products/batch")
	@ApiOperation("批量添加秒杀商品")
	public R<SeckillProductBatchAddResultVO> batchAddProducts(@PathVariable("activityId") Long activityId, @RequestBody @Validated SeckillProductBatchAddDTO dto) {
		SeckillProductBatchAddResultVO result = seckillProductService.batchAddProducts(activityId, dto.getProducts());
		return R.success("批量添加成功", result);
	}

	@PutMapping("/product/{seckillProductId}/stock")
	@ApiOperation("更新秒杀商品库存")
	public R<SeckillProductStockUpdateResultVO> updateStock(@PathVariable("seckillProductId") Long seckillProductId, @RequestBody @Validated SeckillProductStockUpdateDTO dto) {
		SeckillProductStockUpdateResultVO result = seckillProductService.updateStock(seckillProductId, dto);
		return R.success("库存更新成功", result);
	}


	@PostMapping("/activity/{activityId}/preheat")
	@ApiOperation("活动预热（Redis + 本地双层，幂等保护）")
	public R<SeckillActivityPreheatVO> preheatActivity(
			@PathVariable("activityId") Long activityId,
			@RequestParam(value = "force", defaultValue = "false") boolean force) {
		return R.success("预热成功", preheatService.preheatActivity(activityId, force));
	}
}
