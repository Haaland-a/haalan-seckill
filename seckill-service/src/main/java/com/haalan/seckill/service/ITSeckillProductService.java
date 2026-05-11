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


}
