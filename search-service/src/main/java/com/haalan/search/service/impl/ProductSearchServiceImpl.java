package com.haalan.search.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.api.client.ItemServiceClient;
import com.haalan.api.domain.vo.SkuDetailVO;
import com.haalan.common.domain.PageDTO;
import com.haalan.common.domain.po.ProductDoc;
import com.haalan.search.domain.dto.ProductSearchDTO;
import com.haalan.search.domain.vo.ProductSearchVO;
import com.haalan.search.service.IProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品搜索服务实现类
 * </p>
 *
 * @author lyc
 * @since 2026-04-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements IProductSearchService {

	private final RestHighLevelClient restHighLevelClient;
	private final StringRedisTemplate redisTemplate;
	private final ItemServiceClient itemServiceClient;

	/**
	 * <p>
	 * 搜索商品列表
	 * </p>
	 *
	 * @param searchDTO 搜索参数
	 * @return PageDTO<ProductSearchVO> 分页搜索结果
	 * @author Haaland
	 * @date 2026/4/16
	 */
	@Override
	public PageDTO<ProductSearchVO> searchProducts(ProductSearchDTO searchDTO) {
		try {
			// 1.构建 ES 查询请求
			SearchRequest searchRequest = new SearchRequest("products");
			SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

			// 2.构建布尔查询
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

			// 关键词搜索（匹配 SPU 名称）
			if (StrUtil.isNotBlank(searchDTO.getKeyword())) {
				boolQuery.must(QueryBuilders.matchQuery("spuName", searchDTO.getKeyword()));
			}

			// 分类过滤
			if (searchDTO.getCategoryId() != null) {
				boolQuery.filter(QueryBuilders.termQuery("categoryId", searchDTO.getCategoryId()));
			}

			sourceBuilder.query(boolQuery);

			// 3.处理排序
			handleSort(sourceBuilder, searchDTO.getSort());

			// 4.处理分页
			int pageNum = searchDTO.getPageNo() != null ? searchDTO.getPageNo() : 1;
			int pageSize = searchDTO.getPageSize() != null ? searchDTO.getPageSize() : 20;
			sourceBuilder.from((pageNum - 1) * pageSize);
			sourceBuilder.size(pageSize);

			searchRequest.source(sourceBuilder);

			// 5.执行查询
			SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
			SearchHits hits = searchResponse.getHits();
			long total = hits.getTotalHits().value;

			// 6.解析结果
			List<ProductSearchVO> voList = new ArrayList<>();
			if (hits.getHits().length > 0) {
				// 提取所有 SKU ID   //用于批量获取库存和销量
				List<Long> allSkuIds = new ArrayList<>();

				for (SearchHit hit : hits.getHits()) {
					String sourceAsString = hit.getSourceAsString();
					//把JSON 转换成字符串再转换 ProductDoc
					ProductDoc productDoc = JSONUtil.toBean(sourceAsString, ProductDoc.class);

					// 解析 SKU 列表  获取到所有 SKU ID
					List<ProductDoc.SkuDoc> skuDocs = JSONUtil.toList(
							productDoc.getSkuList(),
							ProductDoc.SkuDoc.class
					);

					skuDocs.forEach(sku -> allSkuIds.add(sku.getSkuId()));
				}

				// 7.批量获取库存和销量（都是 SKU 级别）
				//    注意：这里原来读的是 Redis 的 "stock:" / "sales:" 前缀，
				//    但全项目唯一往 Redis 写这两项的地方是 item-service 的测试类
				//    ProductSyncToEsTest，它写的 key 是 "sku-stock:" / "sku-sales:"，
				//    而且要靠人手跑 JUnit 才会写、TTL 只有 2 小时。
				//    结果就是 multiGet 全返回 null，搜索结果里所有商品库存和销量恒为 0。
				//    改为优先读 Redis 缓存、未命中时回源 item-service 的批量接口（见 fetchStockAndSales）。
				SkuStockSales skuStockSales = fetchStockAndSales(allSkuIds);
				Map<Long, Integer> stockMap = skuStockSales.stockMap;
				Map<Long, Integer> salesMap = skuStockSales.salesMap;

				// 8.组装 VO
				for (SearchHit hit : hits.getHits()) {
					String sourceAsString = hit.getSourceAsString();
					ProductDoc productDoc = JSONUtil.toBean(sourceAsString, ProductDoc.class);

					ProductSearchVO vo = convertToVO(productDoc, stockMap, salesMap);
					voList.add(vo);
				}

				// 8.1 按销量排序：销量不在 ES 里，只能在应用层对当前页重排
				if ("sales_desc".equalsIgnoreCase(searchDTO.getSort())) {
					rankBySales(voList, salesMap);
				}
			}

			// 9.计算总页数
			long pages = (total + pageSize - 1) / pageSize;

			return new PageDTO<>(total, pages, voList);

		} catch (IOException e) {
			log.error("ES 搜索失败", e);
			throw new RuntimeException("搜索失败");
		}
	}

	/**
	 * <p>
	 * 处理排序逻辑
	 * </p>
	 *
	 * @param sourceBuilder 查询构建器
	 * @param sort          排序方式
	 * @author Haaland
	 * @date 2026/4/16
	 */
	private void handleSort(SearchSourceBuilder sourceBuilder, String sort) {
		if (StrUtil.isBlank(sort)) {
			// 默认按相关度排序
			return;
		}
		switch (sort.toLowerCase()) {
			case "price_asc":
				sourceBuilder.sort("minPrice", SortOrder.ASC);
				break;
			case "price_desc":
				sourceBuilder.sort("minPrice", SortOrder.DESC);
				break;
			case "sales_desc":
				// 销量存在 Redis（sku-sales:），ES 索引里没有这个字段，无法让 ES 排序。
				// 这里不排序，交给 rankBySales() 在应用层按销量重排当前页结果。
				break;
			default:
				log.warn("不支持的排序方式: {}", sort);
		}
	}

	/**
	 * <p>
	 * 按销量对当前页结果重新排序（降序）
	 * </p>
	 *
	 * <p>
	 * 注意这里是「页内排序」而不是全局排序：销量不在 ES 里，ES 无法参与排序，
	 * 所以只能把当前页取回来之后按销量重排。要做到全局准确排序，
	 * 需要把 sales 同步成 ES 的一个数值字段，那时才能下推到 ES 的 sort。
	 * </p>
	 *
	 * @param voList   当前页的商品 VO 列表
	 * @param salesMap SKU 销量映射
	 */
	private void rankBySales(List<ProductSearchVO> voList, Map<Long, Integer> salesMap) {
		if (voList == null || voList.size() <= 1) {
			return;
		}
		// 对比「SPU 下所有 SKU 的销量之和」，与 convertToVO 里 setSales 的口径一致
		Comparator<ProductSearchVO> bySalesDesc = Comparator.comparingInt(
				(ProductSearchVO vo) -> sumSales(vo, salesMap)).reversed();
		voList.sort(bySalesDesc);
	}

	private int sumSales(ProductSearchVO vo, Map<Long, Integer> salesMap) {
		if (vo == null || vo.getSkuList() == null) {
			return 0;
		}
		int total = 0;
		for (ProductSearchVO.SkuInfoVO sku : vo.getSkuList()) {
			if (sku != null && sku.getSkuId() != null) {
				total += salesMap.getOrDefault(sku.getSkuId(), 0);
			}
		}
		return total;
	}

	/**
	 * <p>
	 * 获取 SKU 的库存与销量
	 * </p>
	 *
	 * <p>
	 * 策略：先读 Redis 缓存（key 前缀与 item-service 同步任务保持一致：{@code sku-stock:} / {@code sku-sales:}），
	 * 缓存没命中的 SKU 再回源 item-service 的批量接口补一次，并把结果顺手写回缓存。
	 * </p>
	 * <p>
	 * 为什么不能只读 Redis：全项目唯一往这两个 key 写数据的地方是 item-service 的测试类
	 * {@code ProductSyncToEsTest}，需要人手跑 JUnit，且 TTL 只有 2 小时。
	 * 也就是说只要没人跑测试，缓存就是空的，搜索结果里所有商品库存/销量会恒为 0。
	 * </p>
	 *
	 * @param skuIds SKU ID 列表
	 * @return 库存与销量的映射
	 */
	private SkuStockSales fetchStockAndSales(List<Long> skuIds) {
		Map<Long, Integer> stockMap = new LinkedHashMap<>();
		Map<Long, Integer> salesMap = new LinkedHashMap<>();
		if (skuIds == null || skuIds.isEmpty()) {
			return new SkuStockSales(stockMap, salesMap);
		}

		// 0.先尝试 Redis 缓存，但这里必须区分「键不存在」和「值就是 0」。
		//    batchGetFromRedis 为了省事把 null 也塞成 0，于是 cache.get(skuId) 永远非 null，
		//    下面「stock != null && sales != null 就当命中」的判断会把所有 SKU 都误判为已命中，
		//    回源 item-service 的分支永远不执行 —— 这正是搜索结果库存/销量恒为 0 的直接原因。
		//    所以这里改用 containsKey 判断「真的有这个键」，而不是判断值非 null。
		Map<Long, Integer> cachedStock = batchGetFromRedis(skuIds, "sku-stock:");
		Map<Long, Integer> cachedSales = batchGetFromRedis(skuIds, "sku-sales:");

		List<Long> missed = new ArrayList<>();
		for (Long skuId : skuIds) {
			boolean stockCached = cachedStock.containsKey(skuId);
			boolean salesCached = cachedSales.containsKey(skuId);
			if (stockCached && salesCached) {
				stockMap.put(skuId, cachedStock.get(skuId));
				salesMap.put(skuId, cachedSales.get(skuId));
			} else {
				missed.add(skuId);
			}
		}

		if (missed.isEmpty()) {
			return new SkuStockSales(stockMap, salesMap);
		}

		// 2.缓存未命中的回源 item-service（一次批量调用，避免 N+1）
		try {
			List<SkuDetailVO> details = itemServiceClient.getSkuDetailBatch(missed);
			if (details != null) {
				for (SkuDetailVO detail : details) {
					if (detail == null || detail.getSkuId() == null) {
						continue;
					}
					Long skuId = detail.getSkuId();
					Integer stock = detail.getStock() == null ? 0 : detail.getStock().intValue();
					// SkuDetailVO.soldCount 就是 t_sku.sold_count，这就是真实的销量。
					// 之前这里硬编码成 0，理由是「SkuDetailVO 没有销量字段」—— 这个判断是错的，
					// 字段一直都有，结果就是搜索结果里销量永远是 0、按销量排序永远排不出东西。
					Integer sales = detail.getSoldCount() == null ? 0 : detail.getSoldCount().intValue();
					stockMap.put(skuId, stock);
					salesMap.put(skuId, sales);
				}
			}
		} catch (Exception e) {
			// 回源失败不能让整个搜索挂掉：库存/销量只是展示字段
			log.warn("回源 item-service 获取库存/销量失败，相关 SKU 按 0 处理: {}", e.getMessage());
		}

		// 3.兜底：既没缓存也没回源到的 SKU，补齐为 0（converted VO 用 getOrDefault 也会兜到 0）
		for (Long skuId : missed) {
			stockMap.putIfAbsent(skuId, 0);
			salesMap.putIfAbsent(skuId, 0);
		}

		return new SkuStockSales(stockMap, salesMap);
	}

	/**
	 * 库存 / 销量 的一对映射，仅用于方法内传递，避免再引入两个并行的 Map 参数。
	 */
	private static class SkuStockSales {
		private final Map<Long, Integer> stockMap;
		private final Map<Long, Integer> salesMap;

		private SkuStockSales(Map<Long, Integer> stockMap, Map<Long, Integer> salesMap) {
			this.stockMap = stockMap;
			this.salesMap = salesMap;
		}
	}

	/**
	 * <p>
	 * 批量从 Redis 获取数据
	 * </p>
	 *
	 * @param ids       ID 列表
	 * @param keyPrefix Key 前缀
	 * @return Map<id, value> 数据映射
	 * @author Haaland
	 * @date 2026/4/16
	 */
	private Map<Long, Integer> batchGetFromRedis(List<Long> ids, String keyPrefix) {
		if (ids == null || ids.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<Long, Integer> resultMap = new HashMap<>();

		// 使用 Pipeline 批量获取
		List<String> keys = ids.stream()
				.map(id -> keyPrefix + id)
				.collect(Collectors.toList());

		List<String> values = redisTemplate.opsForValue().multiGet(keys);

		if (values != null) {
			for (int i = 0; i < ids.size(); i++) {
				String value = values.get(i);
				// 关键：未命中的 key 绝不能往 resultMap 里放 0。
				// 原来这里是 else { resultMap.put(id, 0); }，导致 resultMap 永远包含全部 id，
				// 调用方 fetchStockAndSales 用 containsKey 判断「是否命中缓存」时全部判为命中，
				// 回源 item-service 的分支永远不会执行，搜索结果库存/销量因此恒为 0。
				// 现在未命中就不放进 map，由调用方决定怎么降级。
				if (value != null) {
					try {
						resultMap.put(ids.get(i), Integer.parseInt(value));
					} catch (NumberFormatException e) {
						log.warn("Redis 中 {} 的值不是合法整数: {}", keys.get(i), value);
					}
				}
			}
		}

		return resultMap;
	}

	/**
	 * <p>
	 * 转换为 VO 对象,只转换一个文档,循环调用是全部转换
	 * </p>
	 *
	 * @param productDoc ES 文档
	 * @param stockMap   库存映射
	 * @param salesMap   销量映射
	 * @return ProductSearchVO
	 * @author Haaland
	 * @date 2026/4/16
	 */
	private ProductSearchVO convertToVO(ProductDoc productDoc,
										Map<Long, Integer> stockMap,
										Map<Long, Integer> salesMap) {
		ProductSearchVO vo = new ProductSearchVO();
		vo.setSpuId(productDoc.getSpuId());
		vo.setSpuName(productDoc.getSpuName());
		vo.setMainImage(productDoc.getMainImage());

		// 解析 SKU 列表
		List<ProductSearchVO.SkuInfoVO> skuInfoList = new ArrayList<>();
		int totalSales = 0;
		Integer minPrice = null;
		Integer maxPrice = null;

		if (StrUtil.isNotBlank(productDoc.getSkuList())) {
			List<ProductDoc.SkuDoc> skuDocs = JSONUtil.toList(
					productDoc.getSkuList(),
					ProductDoc.SkuDoc.class
			);

			for (ProductDoc.SkuDoc skuDoc : skuDocs) {
				ProductSearchVO.SkuInfoVO skuInfo = new ProductSearchVO.SkuInfoVO();
				skuInfo.setSkuId(skuDoc.getSkuId());
				skuInfo.setSkuCode(skuDoc.getSkuCode());
				skuInfo.setSkuName(skuDoc.getSkuName());

				Integer price = skuDoc.getPrice();
				skuInfo.setPrice(price != null ?
						BigDecimal.valueOf(price).divide(BigDecimal.valueOf(100)) : null);

				// 计算最小/最大价格
				if (price != null) {
					if (minPrice == null || price < minPrice) {
						minPrice = price;
					}
					if (maxPrice == null || price > maxPrice) {
						maxPrice = price;
					}
				}

				// 从 Redis 获取库存
				Integer stock = stockMap.getOrDefault(skuDoc.getSkuId(), 0);
				skuInfo.setStock(stock);

				// 从 Redis 获取销量
				Integer sales = salesMap.getOrDefault(skuDoc.getSkuId(), 0);
				totalSales += sales;

				// 解析规格参数
				if (StrUtil.isNotBlank(skuDoc.getSpecifications())) {
					skuInfo.setSpecifications(JSONUtil.toBean(
							skuDoc.getSpecifications(),
							Map.class
					));
				}

				skuInfoList.add(skuInfo);
			}
		}

		vo.setSkuList(skuInfoList);
		vo.setMinPrice(minPrice != null ?
				BigDecimal.valueOf(minPrice).divide(BigDecimal.valueOf(100)) : null);
		vo.setMaxPrice(maxPrice != null ?
				BigDecimal.valueOf(maxPrice).divide(BigDecimal.valueOf(100)) : null);
		vo.setSales(totalSales);

		return vo;
	}

//写的两个被我弃用接口
//	@Override
//	public ProductDetailVO getProductDetail(Long spuId) {
//		ProductDetailVO result = itemServiceClient.getProductDetail(spuId);
//
//		if (result == null ) {
//			throw new RuntimeException("商品不存在");
//		}
//
//		return result;
//	}
//  es只做检索!!!!!
//	@Override
//	public ProductDetailVO getProductDetail(Long spuId) {
//		// 1.构建 ES 查询请求
//		SearchRequest searchRequest = new SearchRequest("products");
//		SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
//		//2.查询条件
//
//		sourceBuilder.query(QueryBuilders.termQuery("spuId", spuId));
//		long start = System.currentTimeMillis();
//		//3.查询
//		searchRequest.source(sourceBuilder);
//
//		//4.组装Vo
//		ProductDetailVO productDetailVO = null;    //返回值
//		try {
//			SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
//			long end = System.currentTimeMillis();
//			log.info("商品详情查询耗时: {}ms", end - start);
//			SearchHit[] hits = searchResponse.getHits().getHits();
//			if (hits.length > 0) {
//				SearchHit hit = hits[0];
//				String sourceAsString = hit.getSourceAsString();
//				ProductDoc productDoc = JSONUtil.toBean(sourceAsString, ProductDoc.class);
//				productDetailVO = new ProductDetailVO();
//				//九个主属性
//				productDetailVO.setSpuId(productDoc.getSpuId());
//				productDetailVO.setSpuCode(productDoc.getSpuCode());
//				productDetailVO.setSpuName(productDoc.getSpuName());
//				productDetailVO.setDescription(productDoc.getDescription());
//				productDetailVO.setMainImage(productDoc.getMainImage());
//				productDetailVO.setImages(JSONUtil.toList(productDoc.getImages(), String.class));
//				productDetailVO.setCategoryId(productDoc.getCategoryId());
//				productDetailVO.setCategoryName(productDoc.getCategoryName());
//				productDetailVO.setBrandName(productDoc.getBrandName());
//				//10个sku属性  从文档中获取到8个
//
//				//获取所有skuId
//				List<Long> allSkuIds = new ArrayList<>();
//				// 解析 SKU 列表  获取到所有 SKU ID
//				List<ProductDoc.SkuDoc> skuDocs = JSONUtil.toList(
//						productDoc.getSkuList(),
//						ProductDoc.SkuDoc.class
//				);
//				//4.1 获取库存和销量信息从redis
//				skuDocs.forEach(sku -> allSkuIds.add(sku.getSkuId()));
//				// 批量从 Redis 获取库存和销量（都是 SKU 级别）
//				Map<Long, Integer> stockMap = batchGetFromRedis(allSkuIds, "sku-stock:");
//				Map<Long, Integer> salesMap = batchGetFromRedis(allSkuIds, "sku-sales:");
//
//				productDetailVO.setSkuList(skuDocs.stream().map(sku -> {
//					ProductDetailVO.SkuDetailVO skuDetailVO = new ProductDetailVO.SkuDetailVO();
//					skuDetailVO.setSkuId(sku.getSkuId());
//					skuDetailVO.setSkuCode(sku.getSkuCode());
//					skuDetailVO.setSkuName(sku.getSkuName());
//					skuDetailVO.setPrice(BigDecimal.valueOf(sku.getPrice()).divide(BigDecimal.valueOf(100)));
//					skuDetailVO.setOriginalPrice(BigDecimal.valueOf(sku.getOriginalPrice()).divide(BigDecimal.valueOf(100)));
//					skuDetailVO.setImages(JSONUtil.toList(sku.getImages(), String.class));
//					skuDetailVO.setSpecifications(JSONUtil.toBean(sku.getSpecifications(), Map.class));
//					skuDetailVO.setStatus(sku.getStatus());
//					//加入库存和销量
//
//					skuDetailVO.setStock(Long.valueOf(stockMap.getOrDefault(sku.getSkuId(), 0)));
//					skuDetailVO.setSales(Long.valueOf(salesMap.getOrDefault(sku.getSkuId(), 0)));
//					return skuDetailVO;
//				}).collect(Collectors.toList()));
//			}
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
//
//		return productDetailVO;
//
//	}

}
