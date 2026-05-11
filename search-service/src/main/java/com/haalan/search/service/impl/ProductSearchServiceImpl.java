package com.haalan.search.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.haalan.api.client.ItemServiceClient;
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

				// 7.批量从 Redis 获取库存和销量（都是 SKU 级别）
				Map<Long, Integer> stockMap = batchGetFromRedis(allSkuIds, "stock:");
				Map<Long, Integer> salesMap = batchGetFromRedis(allSkuIds, "sales:");

				// 8.组装 VO
				for (SearchHit hit : hits.getHits()) {
					String sourceAsString = hit.getSourceAsString();
					ProductDoc productDoc = JSONUtil.toBean(sourceAsString, ProductDoc.class);

					ProductSearchVO vo = convertToVO(productDoc, stockMap, salesMap);
					voList.add(vo);
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
		//   完成     排序逻辑 我的es字段中没有minprice,需要改善一些东西,21点了,明天继续,加油
		switch (sort.toLowerCase()) {
			case "price_asc":
				sourceBuilder.sort("minPrice", SortOrder.ASC);
				break;
			case "price_desc":
				sourceBuilder.sort("minPrice", SortOrder.DESC);
				break;
			case "sales_desc":
				//  todo 暂时不支持 销量在 Redis 中，ES 无法直接排序，需要在应用层排序
				log.warn("暂不支持按销量排序");
				break;
			default:
				log.warn("不支持的排序方式: {}", sort);
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
				if (value != null) {
					resultMap.put(ids.get(i), Integer.parseInt(value));
				} else {
					resultMap.put(ids.get(i), 0);
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
