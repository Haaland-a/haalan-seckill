package com.haalan.seckill.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 布隆过滤器工具类
 * <p>
 * 用于防止缓存击穿，在查询数据库之前先通过布隆过滤器判断数据是否存在
 * </p>
 *
 * @author Haaland
 * @date 2026/5/22
 */
@Slf4j
@Component
public class BloomFilterUtil {

	/**
	 * 秒杀活动ID布隆过滤器
	 */
	private BloomFilter<Long> activityBloomFilter;

	/**
	 * 秒杀商品ID布隆过滤器
	 */
	private BloomFilter<Long> productBloomFilter;

	/**
	 * 预期元素数量
	 */
	private static final int EXPECTED_INSERTIONS = 100000;

	/**
	 * 误判率
	 */
	private static final double FPP = 0.01;

	@PostConstruct
	public void init() {
		// 初始化活动布隆过滤器
		activityBloomFilter = BloomFilter.create(
				Funnels.longFunnel(),
				EXPECTED_INSERTIONS,
				FPP
		);

		// 初始化商品布隆过滤器
		productBloomFilter = BloomFilter.create(
				Funnels.longFunnel(),
				EXPECTED_INSERTIONS,
				FPP
		);

		log.info("布隆过滤器初始化完成");
	}

	/**
	 * 向活动布隆过滤器中添加元素
	 *
	 * @param activityId 活动ID
	 */
	public void addActivity(Long activityId) {
		if (activityId != null) {
			activityBloomFilter.put(activityId);
		}
	}

	/**
	 * 向商品布隆过滤器中添加元素
	 *
	 * @param productId 商品ID
	 */
	public void addProduct(Long productId) {
		if (productId != null) {
			productBloomFilter.put(productId);
		}
	}

	/**
	 * 批量添加活动ID到布隆过滤器
	 *
	 * @param activityIds 活动ID列表
	 */
	public void addActivities(List<Long> activityIds) {
		if (activityIds != null && !activityIds.isEmpty()) {
			for (Long activityId : activityIds) {
				addActivity(activityId);
			}
			log.info("批量添加 {} 个活动ID到布隆过滤器", activityIds.size());
		}
	}

	/**
	 * 批量添加商品ID到布隆过滤器
	 *
	 * @param productIds 商品ID列表
	 */
	public void addProducts(List<Long> productIds) {
		if (productIds != null && !productIds.isEmpty()) {
			for (Long productId : productIds) {
				addProduct(productId);
			}
			log.info("批量添加 {} 个商品ID到布隆过滤器", productIds.size());
		}
	}

	/**
	 * 判断活动ID是否可能存在
	 *
	 * @param activityId 活动ID
	 * @return true-可能存在，false-一定不存在
	 */
	public boolean mightContainActivity(Long activityId) {
		if (activityId == null) {
			return false;
		}
		return activityBloomFilter.mightContain(activityId);
	}

	/**
	 * 判断商品ID是否可能存在
	 *
	 * @param productId 商品ID
	 * @return true-可能存在，false-一定不存在
	 */
	public boolean mightContainProduct(Long productId) {
		if (productId == null) {
			return false;
		}
		return productBloomFilter.mightContain(productId);
	}

	/**
	 * 获取活动布隆过滤器
	 *
	 * @return 活动布隆过滤器
	 */
	public BloomFilter<Long> getActivityBloomFilter() {
		return activityBloomFilter;
	}

	/**
	 * 获取商品布隆过滤器
	 *
	 * @return 商品布隆过滤器
	 */
	public BloomFilter<Long> getProductBloomFilter() {
		return productBloomFilter;
	}
}
