package com.haalan.seckill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.seckill.domain.dto.SeckillActivityAddPDTO;
import com.haalan.seckill.domain.dto.SeckillProductStockUpdateDTO;
import com.haalan.seckill.domain.po.TSeckillProduct;
import com.haalan.seckill.domain.vo.SeckillActivityAddPVO;
import com.haalan.seckill.domain.vo.SeckillProductBatchAddResultVO;
import com.haalan.seckill.domain.vo.SeckillProductStockUpdateResultVO;

import java.util.List;

/**
 * <p>
 *
 * @author Haaland
 * @description tSeckillProductService
 * </p>
 * @date 2026/4/24
 */

public interface ITSeckillProductService extends IService<TSeckillProduct> {

	SeckillActivityAddPVO addProduct(Long activityId, SeckillActivityAddPDTO dto);

	SeckillProductBatchAddResultVO batchAddProducts(Long activityId, List<SeckillActivityAddPDTO> products);

	SeckillProductStockUpdateResultVO updateStock(Long seckillProductId, SeckillProductStockUpdateDTO dto);

	/**
	 * 支付成功后扣减数据库库存（从Redis同步到数据库）
	 *
	 * @param seckillProductId 秒杀商品ID
	 * @param quantity         扣减数量
	 */
	void deductStockAfterPayment(Long seckillProductId, Integer quantity);

	/**
	 * 退款成功后回滚数据库库存
	 *
	 * @param seckillProductId 秒杀商品ID
	 * @param quantity         回滚数量
	 */
	void rollbackStockAfterRefund(Long seckillProductId, Integer quantity);

}
