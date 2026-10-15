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

	/**
	 * 累加 SKU 销量。
	 * <p>
	 * 之前全项目没有任何地方累加 {@code t_sku.sold_count}（唯一写入是 createSku 里的 setSoldCount(0)），
	 * 导致商品详情销量、搜索列表销量、sales_desc 排序永远都是 0。
	 * </p>
	 *
	 * @param skuId SKU ID
	 * @param num   增加数量
	 * @return 累加后的销量
	 */
	Integer incrSoldCount(Long skuId, Integer num);

}
