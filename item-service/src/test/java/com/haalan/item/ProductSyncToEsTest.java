package com.haalan.item;

import cn.hutool.json.JSONUtil;
import com.haalan.item.domain.po.TBrand;
import com.haalan.item.domain.po.TCategory;
import com.haalan.item.domain.po.TSku;
import com.haalan.item.domain.po.TSpu;
import com.haalan.item.service.ITBrandService;
import com.haalan.item.service.ITCategoryService;
import com.haalan.item.service.ITSkuService;
import com.haalan.item.service.ITSpuService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品数据同步到 ES 测试类
 * </p>
 *
 * @author lyc
 * @since 2026-04-15
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")

public class ProductSyncToEsTest {

	@Autowired
	private ITSpuService spuService;

	@Autowired
	private ITSkuService skuService;

	@Autowired
	private ITCategoryService categoryService;

	@Autowired
	private ITBrandService brandService;

	@Autowired
	private RestHighLevelClient restHighLevelClient;

	@Autowired
	private StringRedisTemplate redisTemplate;

	/**
	 * <p>
	 * 将数据库中的商品数据同步到 ES
	 * </p>
	 *
	 * @author Haaland
	 * @date 2026/4/15
	 */
	@Test
	public void syncProductsToEs() throws IOException {
		log.info("开始同步商品数据到 ES...");

		// 1.查询所有上架的 SPU
		List<TSpu> spuList = spuService.lambdaQuery()
				.eq(TSpu::getStatus, 1)
				.list();

		if (spuList == null || spuList.isEmpty()) {
			log.warn("没有需要同步的商品数据");
			return;
		}

		log.info("查询到 {} 个 SPU", spuList.size());

		// 2.批量查询分类和品牌
		Set<Long> categoryIds = spuList.stream()
				.map(TSpu::getCategoryId)
				.collect(Collectors.toSet());
		Map<Long, String> categoryMap = categoryService.listByIds(categoryIds).stream()
				.collect(Collectors.toMap(TCategory::getId, TCategory::getName));

		Set<Long> brandIds = spuList.stream()
				.map(TSpu::getBrandId)
				.collect(Collectors.toSet());
		Map<Long, String> brandMap = brandService.listByIds(brandIds).stream()
				.collect(Collectors.toMap(TBrand::getId, TBrand::getName));

		// 3.构建批量请求
		BulkRequest bulkRequest = new BulkRequest();

		for (TSpu spu : spuList) {
			// 查询该 SPU 下的所有 SKU
			List<TSku> skuList = skuService.lambdaQuery()
					.eq(TSku::getSpuId, spu.getId())
					.eq(TSku::getStatus, 1)
					.list();

			if (skuList == null || skuList.isEmpty()) {
				log.warn("SPU {} 没有可用的 SKU，跳过", spu.getId());
				continue;
			}

			// 构建 ES 文档
			Map<String, Object> doc = buildProductDoc(spu, skuList, categoryMap, brandMap);

			// 添加到批量请求
			IndexRequest indexRequest = new IndexRequest("products")
					.id(String.valueOf(spu.getId()))
					.source(JSONUtil.toJsonStr(doc), XContentType.JSON);
			bulkRequest.add(indexRequest);
		}

		// 4.执行批量导入
		if (bulkRequest.numberOfActions() > 0) {
			restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
			log.info("成功同步 {} 个商品到 ES", bulkRequest.numberOfActions());
		} else {
			log.warn("没有商品需要同步");
		}
	}

	/**
	 * <p>
	 * 构建 ES 文档
	 * </p>
	 *
	 * @param spu         SPU 实体
	 * @param skuList     SKU 列表
	 * @param categoryMap 分类映射
	 * @param brandMap    品牌映射
	 * @return Map<String, Object> ES 文档
	 * @author Haaland
	 * @date 2026/4/15
	 */
	private Map<String, Object> buildProductDoc(TSpu spu, List<TSku> skuList,
												Map<Long, String> categoryMap,
												Map<Long, String> brandMap) {
		Map<String, Object> doc = new HashMap<>();

		// SPU 基本信息
		doc.put("spuId", spu.getId());
		doc.put("spuCode", spu.getSpuCode());
		doc.put("spuName", spu.getName());
		doc.put("description", spu.getDescription());
		doc.put("mainImage", spu.getMainImage());
		doc.put("images", spu.getImages());
		doc.put("categoryId", spu.getCategoryId());
		doc.put("categoryName", categoryMap.getOrDefault(spu.getCategoryId(), "未知分类"));
		doc.put("brandId", spu.getBrandId());
		doc.put("brandName", brandMap.getOrDefault(spu.getBrandId(), "未知品牌"));
		doc.put("status", spu.getStatus());
		doc.put("updateTime", LocalDateTime.now());

		// 构建 SKU 列表
		List<Map<String, Object>> skuDocs = new ArrayList<>();
		Integer minPrice = null;
		Integer maxPrice = null;

		for (TSku sku : skuList) {
			Map<String, Object> skuDoc = new HashMap<>();
			skuDoc.put("skuId", sku.getId());
			skuDoc.put("skuCode", sku.getSkuCode());
			skuDoc.put("skuName", sku.getName());

			// 价格转为分
			Integer priceInCent = sku.getPrice() != null ?
					sku.getPrice().multiply(BigDecimal.valueOf(100)).intValue() : null;
			skuDoc.put("price", priceInCent);

			Integer originalPriceInCent = sku.getPromotionPrice() != null ?
					sku.getPromotionPrice().multiply(BigDecimal.valueOf(100)).intValue() : null;
			skuDoc.put("originalPrice", originalPriceInCent);

			// 计算最小/最大价格
			if (priceInCent != null) {
				if (minPrice == null || priceInCent < minPrice) {
					minPrice = priceInCent;
				}
				if (maxPrice == null || priceInCent > maxPrice) {
					maxPrice = priceInCent;
				}
			}

			skuDoc.put("specifications", sku.getSpecifications());
			skuDoc.put("images", sku.getImages());
			skuDoc.put("status", sku.getStatus());

			skuDocs.add(skuDoc);
		}
		doc.put("minPrice", minPrice);
		doc.put("maxPrice", maxPrice);
		doc.put("skuList", JSONUtil.toJsonStr(skuDocs));

		return doc;
	}

	@Test //将销量和库存同步到redis   //有spu的商品,不过如果要卖的话都有spu

	public void syncStockAndSalesToRedis() {
//		// 1.查询所有上架的 SPU
//		List<TSpu> spuList = spuService.lambdaQuery()
//				.eq(TSpu::getStatus, 1)
//				.list();
//		// 2.查询所有的 SKU
//		List<TSku> skuList = null;
//		for (TSpu spu : spuList) {
//			 skuList.addAll(skuService.lambdaQuery()
//					 .eq(TSku::getSpuId, spu.getId())
//					 .eq(TSku::getStatus, 1)
//					 .list());
//		}
		// 1. 查 SPU
		List<TSpu> spuList = spuService.lambdaQuery()
				.eq(TSpu::getStatus, 1)
				.list();

// 2. 提取 ID
		List<Long> spuIds = spuList.stream()
				.map(TSpu::getId)
				.collect(Collectors.toList());

// 3. 一次查 SKU
		List<TSku> skuList = skuService.lambdaQuery()
				.in(TSku::getSpuId, spuIds)
				.list();
		for (TSku sku : skuList) {
			// 获取库存和销量  状态
			Integer stock = sku.getStock();
			Integer sales = sku.getSoldCount();
			Integer status = sku.getStatus();
			// 构建 key
			String key = "sku-stock:" + sku.getId();
			// 构建 value
			String value = stock.toString();

			String salesKey = "sku-sales:" + sku.getId();

			String salesValue = sales.toString();
			//状态
			String statusKey = "sku-status:" + sku.getId();
			String statusValue = status.toString();

			// 保存到 Redis
			redisTemplate.opsForValue().set(key, value, 2, TimeUnit.HOURS);
			redisTemplate.opsForValue().set(salesKey, salesValue, 2, TimeUnit.HOURS);
			redisTemplate.opsForValue().set(statusKey, statusValue, 2, TimeUnit.HOURS);
		}
	}

	@Test   //不管spu只管sku   //加一个状态
	public void syncStockAndSalesToRedis2() {

		// .查询所有上架的 SKU
//		List<TSku> skuList = skuService.lambdaQuery()
//				.eq(TSku::getStatus, 1)
//				.list();
		//查所有sku  到时候看需求定需要哪个
		List<TSku> skuList = skuService.lambdaQuery()
				.list();

		for (TSku sku : skuList) {
			// 获取库存和销量
			Integer stock = sku.getStock();
			Integer sales = sku.getSoldCount();
			Integer status = sku.getStatus();
			// 构建 key
			String key = "sku-stock:" + sku.getId();
			// 构建 value
			String value = stock.toString();

			String salesKey = "sku-sales:" + sku.getId();

			String salesValue = sales.toString();
			//状态
			String statusKey = "sku-status:" + sku.getId();
			String statusValue = status.toString();
			// 保存到 Redis
			redisTemplate.opsForValue().set(key, value, 2, TimeUnit.HOURS);
			redisTemplate.opsForValue().set(salesKey, salesValue, 2, TimeUnit.HOURS);
			redisTemplate.opsForValue().set(statusKey, statusValue, 2, TimeUnit.HOURS);
		}
	}
}
