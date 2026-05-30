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
