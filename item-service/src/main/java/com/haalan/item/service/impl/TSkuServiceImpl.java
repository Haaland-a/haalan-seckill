package com.haalan.item.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haalan.common.domain.po.ProductDoc;
import com.haalan.common.exception.BizIllegalException;
import com.haalan.item.domain.dto.SkuCreateDTO;
import com.haalan.item.domain.dto.SkuStockUpdateDTO;
import com.haalan.item.domain.dto.SkuUpdateDTO;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.domain.vo.SkuCreateResultVO;
import com.haalan.item.domain.vo.SkuStockUpdateResultVO;
import com.haalan.item.mapper.TSkuMapper;
import com.haalan.item.service.ITBrandService;
import com.haalan.item.service.ITCategoryService;
import com.haalan.item.service.ITSkuService;
import com.haalan.item.service.ITSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
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
	private final ITCategoryService categoryService;
	private final ITBrandService brandService;
	private final RestHighLevelClient restHighLevelClient;

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
		sku.setAlipayProductCode(dto.getAlipayProductCode() != null ? dto.getAlipayProductCode() : "FAST_INSTANT_TRADE_PAY");
		sku.setWechatProductCode(dto.getWechatProductCode() != null ? dto.getWechatProductCode() : "FAST_INSTANT_TRADE_PAY");
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

		syncSpuToEs(dto.getSpuId());

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

		String type = dto.getType().toUpperCase();
		Integer currentStock = sku.getStock();

		switch (type) {
			case "ADD":
				currentStock += dto.getStock();
				break;
			case "SUB":
				currentStock -= dto.getStock();
				if (currentStock < 0) {
					throw new BizIllegalException("库存不足");
				}
				break;
			case "SET":
				currentStock = dto.getStock();
				break;
			default:
				throw new BizIllegalException("不支持的操作类型: " + type);
		}

		sku.setStock(currentStock);
		this.updateById(sku);

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
		syncSpuToEs(sku.getSpuId());
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
		syncSpuToEs(sku.getSpuId());
		log.info("SKU {} 状态已更新为 {}", skuId, status == 1 ? "正常" : "禁用");
	}

	/**
	 * <p>
	 * 发送到es
	 * </p>
	 *
	 * @param
	 * @return
	 * @author Haaland
	 * @date 2026/4/16
	 */
	private void syncSpuToEs(Long spuId) {
		try {
			TSpu spu = spuService.getById(spuId);
			if (spu == null) {
				log.warn("SPU {} 不存在", spuId);
				return;
			}

			List<TSku> skuList = this.lambdaQuery()
					.eq(TSku::getSpuId, spuId)
					.eq(TSku::getStatus, 1)
					.list();

			if (skuList == null || skuList.isEmpty()) {
				log.warn("SPU {} 没有可用的 SKU，跳过同步", spuId);
				return;
			}

			TCategory category = categoryService.getById(spu.getCategoryId());
			TBrand brand = brandService.getById(spu.getBrandId());

			ProductDoc productDoc = buildProductDoc(spu, skuList, category, brand);

			IndexRequest request = new IndexRequest("products")
					.id(String.valueOf(spuId))
					.source(JSONUtil.toJsonStr(productDoc), XContentType.JSON);

			restHighLevelClient.index(request, RequestOptions.DEFAULT);
			log.info("成功同步 SPU {} 到 ES", spuId);

		} catch (IOException e) {
			log.error("同步 SPU {} 到 ES 失败", spuId, e);
		}
	}

	/**
	 * <p>
	 * 构建商品文档
	 * </p>
	 *
	 * @param
	 * @return
	 * @author Haaland
	 * @date 2026/4/16
	 */
	private ProductDoc buildProductDoc(TSpu spu, List<TSku> skuList,
									   TCategory category, TBrand brand) {
		ProductDoc productDoc = new ProductDoc();

		productDoc.setSpuId(spu.getId());
		productDoc.setSpuCode(spu.getSpuCode());
		productDoc.setSpuName(spu.getName());
		productDoc.setDescription(spu.getDescription());
		productDoc.setMainImage(spu.getMainImage());
		productDoc.setImages(spu.getImages());
		productDoc.setCategoryId(spu.getCategoryId());
		productDoc.setCategoryName(category != null ? category.getName() : "未知分类");
		productDoc.setBrandId(spu.getBrandId());
		productDoc.setBrandName(brand != null ? brand.getName() : "未知品牌");
		productDoc.setStatus(spu.getStatus());

		List<ProductDoc.SkuDoc> skuDocs = new ArrayList<>();

		for (TSku sku : skuList) {
			ProductDoc.SkuDoc skuDoc = new ProductDoc.SkuDoc();
			skuDoc.setSkuId(sku.getId());
			skuDoc.setSkuCode(sku.getSkuCode());
			skuDoc.setSkuName(sku.getName());

			Integer priceInCent = sku.getPrice() != null ?
					sku.getPrice().multiply(BigDecimal.valueOf(100)).intValue() : null;
			skuDoc.setPrice(priceInCent);

			Integer originalPriceInCent = sku.getPromotionPrice() != null ?
					sku.getPromotionPrice().multiply(BigDecimal.valueOf(100)).intValue() : null;
			skuDoc.setOriginalPrice(originalPriceInCent);

			skuDoc.setSpecifications(sku.getSpecifications());
			skuDoc.setImages(sku.getImages());
			skuDoc.setStatus(sku.getStatus());

			skuDocs.add(skuDoc);
		}

		productDoc.setSkuList(JSONUtil.toJsonStr(skuDocs));

		return productDoc;
	}

}

