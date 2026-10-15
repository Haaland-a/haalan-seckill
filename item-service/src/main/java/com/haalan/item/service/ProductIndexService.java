package com.haalan.item.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haalan.common.domain.po.ProductDoc;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.mapper.TSkuMapper;
import com.haalan.item.mapper.TSpuMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 商品索引（ES）同步服务
 * </p>
 *
 * <p>
 * 单独抽成一个组件的直接原因是避免循环依赖：SPU 下架要级联操作 SKU，
 * SKU 变更又要重建 SPU 的 ES 文档，两边互相注入 Service 会形成环。
 * 这里只依赖 Mapper 和无环的 category/brand Service，SPU/SKU 两个 Service 都能安全注入它。
 * </p>
 *
 * <p>
 * 同时修正了原 {@code TSkuServiceImpl#syncSpuToEs} 的一个一致性缺陷：
 * 原来在「没有可用 SKU」时直接 return 跳过同步，导致最后一个 SKU 下架后
 * ES 里仍残留旧文档，用户还能搜到并点开这个商品。
 * 现在的语义是：只要商品不满足可卖条件，就主动删除索引。
 * </p>
 *
 * @author Haaland
 * @date 2026/10/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

	private final TSpuMapper spuMapper;
	private final TSkuMapper skuMapper;
	private final ITCategoryService categoryService;
	private final ITBrandService brandService;
	private final RestHighLevelClient restHighLevelClient;

	/**
	 * 同步 SPU 到 ES。
	 * <p>
	 * 幂等地重建整个文档：可卖则写入最新数据，不可卖（SPU 下架 / 无上架 SKU）则删除文档。
	 * </p>
	 *
	 * @param spuId SPU ID
	 */
	public void syncSpuToEs(Long spuId) {
		if (spuId == null) {
			return;
		}
		try {
			TSpu spu = spuMapper.selectById(spuId);
			if (spu == null) {
				log.warn("SPU {} 不存在", spuId);
				deleteSpuFromEs(spuId);
				return;
			}

			// SPU 自己下架了 → 索引里不该还有它
			if (spu.getStatus() == null || spu.getStatus() != 1) {
				log.info("SPU {} 已下架，从 ES 删除", spuId);
				deleteSpuFromEs(spuId);
				return;
			}

			// 只索引上架的 SKU（status=1）
			List<TSku> skuList = skuMapper.selectList(new LambdaQueryWrapper<TSku>()
					.eq(TSku::getSpuId, spuId)
					.eq(TSku::getStatus, 1));

			// 关键修正：原来这里是 return，导致最后一个 SKU 下架后 ES 残留旧文档
			if (skuList == null || skuList.isEmpty()) {
				log.info("SPU {} 没有上架的 SKU，从 ES 删除以避免残留", spuId);
				deleteSpuFromEs(spuId);
				return;
			}

			TCategory category = categoryService.getById(spu.getCategoryId());
			TBrand brand = brandService.getById(spu.getBrandId());

			ProductDoc productDoc = buildProductDoc(spu, skuList, category, brand);

			IndexRequest request = new IndexRequest("products")
					.id(String.valueOf(spuId))
					.source(JSONUtil.toJsonStr(productDoc), XContentType.JSON);

			restHighLevelClient.index(request, RequestOptions.DEFAULT);
			log.info("成功同步 SPU {} 到 ES，上架SKU数: {}", spuId, skuList.size());

		} catch (IOException e) {
			log.error("同步 SPU {} 到 ES 失败", spuId, e);
		}
	}

	/**
	 * 从 ES 删除 SPU 文档。
	 * <p>
	 * 删除不存在的文档 ES 会返回 NOT_FOUND，这里按幂等操作处理，不抛异常。
	 * </p>
	 *
	 * @param spuId SPU ID
	 */
	public void deleteSpuFromEs(Long spuId) {
		if (spuId == null) {
			return;
		}
		try {
			DeleteRequest request = new DeleteRequest("products")
					.id(String.valueOf(spuId));
			restHighLevelClient.delete(request, RequestOptions.DEFAULT);
			log.info("成功从 ES 删除 SPU {}", spuId);
		} catch (IOException e) {
			log.error("从 ES 删除 SPU {} 失败", spuId, e);
		}
	}

	/**
	 * 全量重建商品索引（DB 为准，覆盖 ES）。
	 * <p>
	 * 解决历史存量漂移：过去 ES 的写入只挂在少数几个写操作上（且「无上架 SKU」时直接 return 跳过），
	 * 导致索引里既有已下架商品的残留文档，又有大量该在却没在的商品。只能靠这个入口一次性收敛。
	 * </p>
	 * <p>
	 * 顺序很重要：先把 DB 中「所有 SPU」都 sync 一遍（可卖的写入、不可卖的删除），
	 * 再删索引中「DB 已不存在」的文档，避免中途重启丢一部分。
	 * </p>
	 *
	 * @return 重建统计
	 */
	public ReindexResult rebuildAllIndex() {
		ReindexResult result = new ReindexResult();

		List<TSpu> allSpus = spuMapper.selectList(null);
		result.setDbSpuCount(allSpus == null ? 0 : allSpus.size());

		for (TSpu spu : allSpus) {
			syncSpuToEs(spu.getId());
			result.setSynced(result.getSynced() + 1);
		}

		// 删掉索引里存在、但 DB 里已没有的孤儿文档
		java.util.Set<Long> dbIds = new java.util.HashSet<>();
		for (TSpu spu : allSpus) {
			dbIds.add(spu.getId());
		}
		try {
			org.elasticsearch.search.builder.SearchSourceBuilder sourceBuilder =
					new org.elasticsearch.search.builder.SearchSourceBuilder();
			sourceBuilder.query(org.elasticsearch.index.query.QueryBuilders.matchAllQuery());
			sourceBuilder.fetchSource(new String[]{"spuId"}, null);
			sourceBuilder.size(10000);
			SearchRequest searchRequest = new SearchRequest("products");
			searchRequest.source(sourceBuilder);
			SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
			for (SearchHit hit : response.getHits().getHits()) {
				try {
					Long esSpuId = Long.valueOf(hit.getId());
					if (!dbIds.contains(esSpuId)) {
						deleteSpuFromEs(esSpuId);
						result.setOrphanDeleted(result.getOrphanDeleted() + 1);
					}
				} catch (NumberFormatException e) {
					log.warn("ES 文档 id 不是数字，跳过: {}", hit.getId());
				}
			}
		} catch (IOException e) {
			log.error("清理 ES 孤儿文档失败（索引数据仍以 DB 同步部分为准）", e);
		}

		log.info("全量重建索引完成: {}", result);
		return result;
	}

	/**
	 * 重建结果统计
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ReindexResult {
		private int dbSpuCount;
		private int synced;
		private int orphanDeleted;
	}

	/**
	 * 构建商品文档
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
