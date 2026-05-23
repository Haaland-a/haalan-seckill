package com.haalan.item.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 商品服务布隆过滤器工具类
 * <p>
 * 用于防止缓存击穿，在查询数据库之前先通过布隆过滤器判断数据是否存在
 * </p>
 *
 * @author Haaland
 * @date 2026/5/22
 */
@Slf4j
@Component
public class ItemBloomFilterUtil {

	/**
	 * 商品SPU ID布隆过滤器
	 */
	private BloomFilter<Long> spuBloomFilter;

	/**
	 * 商品SKU ID布隆过滤器
	 */
	private BloomFilter<Long> skuBloomFilter;

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
		// 初始化SPU布隆过滤器
		spuBloomFilter = BloomFilter.create(
				Funnels.longFunnel(),
				EXPECTED_INSERTIONS,
				FPP
		);

		// 初始化SKU布隆过滤器
		skuBloomFilter = BloomFilter.create(
				Funnels.longFunnel(),
				EXPECTED_INSERTIONS,
				FPP
		);

		log.info("商品服务布隆过滤器初始化完成");
	}

	/**
	 * 向SPU布隆过滤器中添加元素
	 *
	 * @param spuId SPU ID
	 */
	public void addSpu(Long spuId) {
		if (spuId != null) {
			spuBloomFilter.put(spuId);
		}
	}

	/**
	 * 向SKU布隆过滤器中添加元素
	 *
	 * @param skuId SKU ID
	 */
	public void addSku(Long skuId) {
		if (skuId != null) {
			skuBloomFilter.put(skuId);
		}
	}

	/**
	 * 批量添加SPU ID到布隆过滤器
	 *
	 * @param spuIds SPU ID列表
	 */
	public void addSpus(List<Long> spuIds) {
		if (spuIds != null && !spuIds.isEmpty()) {
			for (Long spuId : spuIds) {
				addSpu(spuId);
			}
			log.info("批量添加 {} 个SPU ID到布隆过滤器", spuIds.size());
		}
	}

	/**
	 * 批量添加SKU ID到布隆过滤器
	 *
	 * @param skuIds SKU ID列表
	 */
	public void addSkus(List<Long> skuIds) {
		if (skuIds != null && !skuIds.isEmpty()) {
			for (Long skuId : skuIds) {
				addSku(skuId);
			}
			log.info("批量添加 {} 个SKU ID到布隆过滤器", skuIds.size());
		}
	}

	/**
	 * 判断SPU ID是否可能存在
	 *
	 * @param spuId SPU ID
	 * @return true-可能存在，false-一定不存在
	 */
	public boolean mightContainSpu(Long spuId) {
		if (spuId == null) {
			return false;
		}
		return spuBloomFilter.mightContain(spuId);
	}

	/**
	 * 判断SKU ID是否可能存在
	 *
	 * @param skuId SKU ID
	 * @return true-可能存在，false-一定不存在
	 */
	public boolean mightContainSku(Long skuId) {
		if (skuId == null) {
			return false;
		}
		return skuBloomFilter.mightContain(skuId);
	}

	/**
	 * 获取SPU布隆过滤器
	 *
	 * @return SPU布隆过滤器
	 */
	public BloomFilter<Long> getSpuBloomFilter() {
		return spuBloomFilter;
	}

	/**
	 * 获取SKU布隆过滤器
	 *
	 * @return SKU布隆过滤器
	 */
	public BloomFilter<Long> getSkuBloomFilter() {
		return skuBloomFilter;
	}
}
