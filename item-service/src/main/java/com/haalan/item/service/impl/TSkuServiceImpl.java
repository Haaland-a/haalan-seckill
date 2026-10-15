package com.haalan.item.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.item.domain.dto.SkuCreateDTO;
import com.haalan.item.domain.dto.SkuStockUpdateDTO;
import com.haalan.item.domain.dto.SkuUpdateDTO;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.domain.vo.SkuCreateResultVO;
import com.haalan.item.domain.vo.SkuStockUpdateResultVO;
import com.haalan.item.mapper.TSkuMapper;
import com.haalan.item.service.ITBrandService;
import com.haalan.item.service.ITCategoryService;
import com.haalan.item.service.ITSkuService;
import com.haalan.item.service.ITSpuService;
import com.haalan.item.service.ProductIndexService;
import com.haalan.item.util.ItemCacheEmptyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 商品SKU表 服务实现类
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TSkuServiceImpl extends ServiceImpl<TSkuMapper, TSku> implements ITSkuService {

	private final ITSpuService spuService;
	/**
	 * ES 索引统一出入口，见 {@link ProductIndexService} 的类注释。
	 * <p>
	 * 抽成独立组件的直接原因是避免循环依赖：SKU 变更要重建所属 SPU 的 ES 文档，
	 * 而 SPU 下架又要级联改 SKU，两边互相注入 Service 会成环
	 * （启动时报 BeanCurrentlyInCreationException）。
	 * 这里只依赖 Mapper 和无环的 category/brand Service。
	 * </p>
	 */
	private final ProductIndexService productIndexService;
	private final ItemCacheEmptyUtil itemCacheEmptyUtil;

	/**
	 * 空串/空白串都算未填写，避免把 "" 写进 alipay_product_code 导致收银台「订单信息无法识别」
	 */
	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public SkuCreateResultVO createSku(SkuCreateDTO dto) {
		TSpu spu = spuService.getById(dto.getSpuId());
		if (spu == null) {
			throw new BizIllegalException("SPU不存在");
		}   // 判断SPU是否存在 从另一个表中查询

		TSku existingSku = this.lambdaQuery()
				.eq(TSku::getSkuCode, dto.getSkuCode())
				.one();
		if (existingSku != null) {
			throw new BizIllegalException("SKU编码已存在");
		}

		TSku sku = new TSku();
		sku.setSkuCode(dto.getSkuCode());
		sku.setSpuId(dto.getSpuId());
		sku.setName(dto.getName());
		sku.setAlipayProductCode(isBlank(dto.getAlipayProductCode()) ? "FAST_INSTANT_TRADE_PAY" : dto.getAlipayProductCode());
		if (dto.getSpecifications() != null) {
			sku.setSpecifications(JSONUtil.toJsonStr(dto.getSpecifications()));
		}
		sku.setPrice(dto.getPrice());
		sku.setPromotionPrice(dto.getPromotionPrice());
		sku.setStock(dto.getStock());
		sku.setSoldCount(0);
		if (dto.getImages() != null) {
			sku.setImages(JSONUtil.toJsonStr(dto.getImages()));
		}
		sku.setStatus(1);
		this.save(sku);

		// 新增 SKU 后，所属商品的详情缓存（里面带着 skuList）已经过期，必须失效
		itemCacheEmptyUtil.invalidateProductRelatedCache(dto.getSpuId(),
				Collections.singletonList(sku.getId()));
		productIndexService.syncSpuToEs(dto.getSpuId());

		return SkuCreateResultVO.builder()
				.skuId(sku.getId())
				.build();
	}

	/**
	 * <p>
	 * 修改sku
	 * </p>
	 *
	 * @param
	 * @return
	 * @author Haaland
	 * @date 2026/4/16
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SkuStockUpdateResultVO updateStock(Long skuId, SkuStockUpdateDTO dto) {
		TSku sku = this.getById(skuId);
		if (sku == null) {
			throw new BizIllegalException("SKU不存在");
		}  // 判断SKU是否存在

		// 不能再用「读出来 + 内存里加减 + updateById 写回」的老写法：
		// 那是读-改-写，两个并发请求（管理端改库存 vs 用户下单）会各自读到同一个旧值，
		// 后写的把先写的覆盖回去，库存凭空丢一截；SUB 分支的 if (currentStock < 0)
		// 判断读的也是快照，拦不住并发超卖。
		// 改成把加减下推给 MySQL（SET stock = stock - n），并用 where stock >= n 做条件，
		// 由数据库行锁保证原子性。同一仓 TProductServiceImpl.deductStock 就是这个写法。
		String type = dto.getType().toUpperCase();
		Integer delta = dto.getStock();
		Integer currentStock;

		switch (type) {
			case "ADD":
				this.lambdaUpdate()
						.eq(TSku::getId, skuId)
						.setSql("stock = stock + " + delta)
						.update();
				currentStock = this.getById(skuId).getStock();
				break;
			case "SUB":
				// ge(TSku::getStock, delta) 拼成 where stock >= delta；
				// 影响行数为 0 说明库存不够，一并按库存不足处理
				boolean ok = this.lambdaUpdate()
						.eq(TSku::getId, skuId)
						.ge(TSku::getStock, delta)
						.setSql("stock = stock - " + delta)
						.update();
				if (!ok) {
					throw new BizIllegalException("库存不足");
				}
				currentStock = this.getById(skuId).getStock();
				break;
			case "SET":
				// 直接覆盖没有「相对量」语义，原子加减用不上
				sku.setStock(delta);
				this.updateById(sku);
				currentStock = delta;
				break;
			default:
				throw new BizIllegalException("不支持的操作类型: " + type);
		}

		// 库存变了，所有缓存了库存的地方都要失效：
		// sku-stock:{skuId}（搜索页库存）、sku:detail:{skuId}、product:detail:{spuId}（详情里带 stock）
		// 原来这里一个都没清，导致管理端改了库存，搜索列表和详情页最长 5 分钟还是旧数字。
		itemCacheEmptyUtil.invalidateProductRelatedCache(sku.getSpuId(),
				Collections.singletonList(skuId));

		return SkuStockUpdateResultVO.builder()
				.skuId(skuId)
				.currentStock(currentStock)
				.build();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateSkuInfo(Long skuId, SkuUpdateDTO dto) {
		TSku sku = this.getById(skuId);
		if (sku == null) {
			throw new BizIllegalException("SKU不存在");
		}
		sku.setName(dto.getName());
		sku.setPrice(dto.getPrice());
		sku.setPromotionPrice(dto.getPromotionPrice());
		if (dto.getSpecifications() != null) {
			sku.setSpecifications(JSONUtil.toJsonStr(dto.getSpecifications()));
		}
		if (dto.getImages() != null) {
			sku.setImages(JSONUtil.toJsonStr(dto.getImages()));
		}
		this.updateById(sku);

		// 名称/价格/规格/图片变了：SKU 详情缓存和所属商品详情缓存都要失效
		itemCacheEmptyUtil.invalidateProductRelatedCache(sku.getSpuId(),
				Collections.singletonList(skuId));
		productIndexService.syncSpuToEs(sku.getSpuId());

		log.info("SKU {} 信息已更新", skuId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateSkuStatus(Long skuId, Integer status) {
		if (status != 0 && status != 1) {
			throw new BizIllegalException("状态值无效，必须为 0（禁用）或 1（正常）");
		}
		TSku sku = this.getById(skuId);
		if (sku == null) {
			throw new BizIllegalException("SKU不存在");
		}
		sku.setStatus(status);
		this.updateById(sku);

		// 上架/下架：清 SKU 详情缓存 + 所属商品详情缓存（用户端详情只返回 status=1 的 SKU）
		itemCacheEmptyUtil.invalidateProductRelatedCache(sku.getSpuId(),
				Collections.singletonList(skuId));
		// ES 同步内部会判断：SPU 下架或已无上架 SKU 时删除索引，避免用户还能搜到已下架商品
		productIndexService.syncSpuToEs(sku.getSpuId());

		log.info("SKU {} 状态已更新为 {}", skuId, status == 1 ? "正常" : "禁用");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Integer incrSoldCount(Long skuId, Integer num) {
		if (skuId == null || num == null || num <= 0) {
			return 0;
		}
		TSku sku = this.getById(skuId);
		if (sku == null) {
			throw new BizIllegalException("SKU不存在");
		}
		// 同样下推给数据库做自增，避免读-改-写丢增量
		this.lambdaUpdate()
				.eq(TSku::getId, skuId)
				.setSql("sold_count = sold_count + " + num)
				.update();
		Integer current = this.getById(skuId).getSoldCount();

		// 销量变了：sku-sales:{skuId}、sku:detail:{skuId}、product:detail:{spuId} 都要失效
		itemCacheEmptyUtil.invalidateProductRelatedCache(sku.getSpuId(),
				Collections.singletonList(skuId));
		return current == null ? 0 : current;
	}

}

