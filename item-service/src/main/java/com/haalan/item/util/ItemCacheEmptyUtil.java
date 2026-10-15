package com.haalan.item.util;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 商品服务缓存空对象工具类
 * <p>
 * 用于防止缓存击穿，当查询数据库为空时，也将空结果缓存到Redis中
 * </p>
 *
 * @author Haaland
 * @date 2026/5/22
 */
@Slf4j
@Component
public class ItemCacheEmptyUtil {

	// 修改后 - 使用 Spring Boot 自动配置的 stringRedisTemplate
	@Resource(name = "stringRedisTemplate")
	private StringRedisTemplate stringRedisTemplate;

	/**
	 * 空值标记
	 */
	private static final String EMPTY_VALUE = "__EMPTY__";

	/**
	 * 空值缓存时间（秒）- 较短时间，避免数据不一致
	 */
	private static final long EMPTY_CACHE_TTL = 60;

	/**
	 * 正常数据缓存时间（秒）
	 */
	private static final long NORMAL_CACHE_TTL = 300;

	/**
	 * 从缓存中获取数据，如果不存在则查询数据库并缓存
	 *
	 * @param key         缓存键
	 * @param dbQueryFunc 数据库查询函数
	 * @param clazz       返回类型
	 * @param <T>         泛型类型
	 * @return 查询结果
	 */
	public <T> T getOrCache(String key, DbQueryFunc<T> dbQueryFunc, Class<T> clazz) {
		// 1. 先从缓存中获取
		String cachedValue = stringRedisTemplate.opsForValue().get(key);

		// 2. 如果缓存中存在
		if (cachedValue != null) {
			// 2.1 如果是空值标记，直接返回null
			if (EMPTY_VALUE.equals(cachedValue)) {
				log.debug("缓存命中空值: {}", key);
				return null;
			}

			// 2.2 否则解析缓存值并返回
			try {
				T result = JSONUtil.toBean(cachedValue, clazz);
				log.debug("缓存命中: {}", key);
				return result;
			} catch (Exception e) {
				log.warn("解析缓存数据失败: {}, 将重新查询数据库", key, e);
			}
		}

		// 3. 缓存未命中，查询数据库
		T dbResult = dbQueryFunc.query();

		// 4. 将结果写入缓存
		if (dbResult == null) {
			// 4.1 数据库结果为空，缓存空值标记
			stringRedisTemplate.opsForValue().set(key, EMPTY_VALUE, EMPTY_CACHE_TTL, TimeUnit.SECONDS);
			log.debug("数据库查询为空，已缓存空值: {}", key);
		} else {
			// 4.2 数据库结果不为空，缓存实际数据
			String jsonValue = JSONUtil.toJsonStr(dbResult);
			stringRedisTemplate.opsForValue().set(key, jsonValue, NORMAL_CACHE_TTL, TimeUnit.SECONDS);
			log.debug("数据库查询成功，已缓存数据: {}", key);
		}

		return dbResult;
	}

	/**
	 * 从缓存中获取列表数据，如果不存在则查询数据库并缓存
	 *
	 * @param key         缓存键
	 * @param dbQueryFunc 数据库查询函数
	 * @param clazz       列表元素类型
	 * @param <T>         泛型类型
	 * @return 查询结果列表
	 */
	public <T> java.util.List<T> getOrCacheList(String key, DbQueryFunc<java.util.List<T>> dbQueryFunc, Class<T> clazz) {
		// 1. 先从缓存中获取
		String cachedValue = stringRedisTemplate.opsForValue().get(key);

		// 2. 如果缓存中存在
		if (cachedValue != null) {
			// 2.1 如果是空值标记，直接返回空列表
			if (EMPTY_VALUE.equals(cachedValue)) {
				log.debug("缓存命中空值列表: {}", key);
				return java.util.Collections.emptyList();
			}

			// 2.2 否则解析缓存值并返回
			try {
				java.util.List<T> result = JSONUtil.toList(cachedValue, clazz);
				log.debug("缓存命中列表: {}", key);
				return result;
			} catch (Exception e) {
				log.warn("解析缓存列表数据失败: {}, 将重新查询数据库", key, e);
			}
		}

		// 3. 缓存未命中，查询数据库
		java.util.List<T> dbResult = dbQueryFunc.query();

		// 4. 将结果写入缓存
		if (dbResult == null || dbResult.isEmpty()) {
			// 4.1 数据库结果为空，缓存空值标记
			stringRedisTemplate.opsForValue().set(key, EMPTY_VALUE, EMPTY_CACHE_TTL, TimeUnit.SECONDS);
			log.debug("数据库查询为空列表，已缓存空值: {}", key);
			return java.util.Collections.emptyList();
		} else {
			// 4.2 数据库结果不为空，缓存实际数据
			String jsonValue = JSONUtil.toJsonStr(dbResult);
			stringRedisTemplate.opsForValue().set(key, jsonValue, NORMAL_CACHE_TTL, TimeUnit.SECONDS);
			log.debug("数据库查询成功，已缓存列表数据: {}", key);
		}

		return dbResult;
	}

	/**
	 * 删除缓存
	 *
	 * @param key 缓存键
	 */
	public void deleteCache(String key) {
		stringRedisTemplate.delete(key);
		log.debug("已删除缓存: {}", key);
	}

	/**
	 * 批量删除缓存
	 *
	 * @param keys 缓存键列表
	 */
	public void deleteCacheBatch(java.util.Collection<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return;
		}
		stringRedisTemplate.delete(keys);
		log.debug("已批量删除缓存, count={}", keys.size());
	}

	/**
	 * 商品详情缓存 key 前缀。
	 * <p>
	 * 必须与 {@code TProductServiceImpl#getProductDetail} 里读缓存用的 key 字面量一致，
	 * 所以在这里统一定义生成方法：写操作（改状态/改库存/改价）失效缓存时调用它，
	 * 避免 Service 层各写一遍字符串，一旦前缀写歪就「删了假缓存」，用户端继续读旧数据。
	 */
	private static final String PRODUCT_DETAIL_KEY_PREFIX = "product:detail:";

	/**
	 * SKU 详情缓存 key 前缀
	 */
	private static final String SKU_DETAIL_KEY_PREFIX = "sku:detail:";

	/**
	 * SPU 详情缓存 key
	 */
	public static String productDetailKey(Long spuId) {
		return PRODUCT_DETAIL_KEY_PREFIX + spuId;
	}

	/**
	 * SKU 详情缓存 key
	 */
	public static String skuDetailKey(Long skuId) {
		return SKU_DETAIL_KEY_PREFIX + skuId;
	}

	/**
	 * SKU 库存缓存 key 前缀。
	 * <p>
	 * 与 search-service 的 {@code ProductSearchServiceImpl#fetchStockAndSales} 读取的前缀必须一致，
	 * 之前搜索页库存/销量恒为 0 就是因为两边前缀对不上（那边读 "stock:"/"sales:"，这边写 "sku-stock:"/"sku-sales:"）。
	 */
	private static final String SKU_STOCK_KEY_PREFIX = "sku-stock:";

	/**
	 * SKU 销量缓存 key 前缀
	 */
	private static final String SKU_SALES_KEY_PREFIX = "sku-sales:";

	/**
	 * SKU 库存缓存 key
	 */
	public static String skuStockKey(Long skuId) {
		return SKU_STOCK_KEY_PREFIX + skuId;
	}

	/**
	 * SKU 销量缓存 key
	 */
	public static String skuSalesKey(Long skuId) {
		return SKU_SALES_KEY_PREFIX + skuId;
	}

	/**
	 * 一次性失效某个 SPU 及其全部 SKU 的所有缓存（详情 / SKU 详情 / 库存 / 销量）。
	 * <p>
	 * 商品侧所有会改数据的写操作（改状态、改库存、改价、上架下架、删除）都应该调用这里，
	 * 而不是各自只清一部分。之前「管理端下架了、用户端还能看能买」就是漏清导致的。
	 *
	 * @param spuId  SPU ID，可为 null（只清 SKU 维度时传 null）
	 * @param skuIds 需要失效的 SKU ID 集合，可为 null 或空
	 */
	public void invalidateProductRelatedCache(Long spuId, java.util.Collection<Long> skuIds) {
		java.util.List<String> keys = new java.util.ArrayList<>();
		if (spuId != null) {
			keys.add(productDetailKey(spuId));
		}
		if (skuIds != null) {
			for (Long skuId : skuIds) {
				if (skuId == null) {
					continue;
				}
				keys.add(skuDetailKey(skuId));
				keys.add(skuStockKey(skuId));
				keys.add(skuSalesKey(skuId));
			}
		}
		deleteCacheBatch(keys);
	}

	/**
	 * 数据库查询函数接口
	 *
	 * @param <T> 返回类型
	 */
	@FunctionalInterface
	public interface DbQueryFunc<T> {
		/**
		 * 执行数据库查询
		 *
		 * @return 查询结果
		 */
		T query();
	}
}
