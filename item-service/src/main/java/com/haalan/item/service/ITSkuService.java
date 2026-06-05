package com.haalan.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haalan.item.domain.dto.SkuCreateDTO;
import com.haalan.item.domain.dto.SkuStockUpdateDTO;
import com.haalan.item.domain.dto.SkuUpdateDTO;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.vo.SkuCreateResultVO;
import com.haalan.item.domain.vo.SkuStockUpdateResultVO;

/**
 * <p>
 * 商品SKU表 服务类
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
public interface ITSkuService extends IService<TSku> {

	SkuCreateResultVO createSku(SkuCreateDTO dto);

	SkuStockUpdateResultVO updateStock(Long skuId, SkuStockUpdateDTO dto);

	void updateSkuInfo(Long skuId, SkuUpdateDTO dto);

	void updateSkuStatus(Long skuId, Integer status);

}
