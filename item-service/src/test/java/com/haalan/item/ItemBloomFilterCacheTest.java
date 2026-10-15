package com.haalan.item;

import com.haalan.item.util.ItemBloomFilterUtil;
import com.haalan.item.util.ItemCacheEmptyUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * 商品服务布隆过滤器和缓存空对象测试类
 *
 * @author Haaland
 * @date 2026/5/22
 */
@SpringBootTest
@Slf4j
public class ItemBloomFilterCacheTest {

    @Autowired
    private ItemBloomFilterUtil itemBloomFilterUtil;

    @Autowired
    private ItemCacheEmptyUtil itemCacheEmptyUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 测试商品布隆过滤器基本功能
     */
    @Test
    public void testItemBloomFilter() {
        log.info("开始测试商品布隆过滤器基本功能");

        // 添加一些测试数据
        List<Long> spuIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        List<Long> skuIds = Arrays.asList(10L, 20L, 30L, 40L, 50L);
        
        itemBloomFilterUtil.addSpus(spuIds);
        itemBloomFilterUtil.addSkus(skuIds);

        // 测试存在的SPU数据
        for (Long id : spuIds) {
            boolean exists = itemBloomFilterUtil.mightContainSpu(id);
            log.info("SPU ID {} 是否存在: {}", id, exists);
        }

        // 测试存在的SKU数据
        for (Long id : skuIds) {
            boolean exists = itemBloomFilterUtil.mightContainSku(id);
            log.info("SKU ID {} 是否存在: {}", id, exists);
        }

        // 测试不存在的数据（可能会有误判）
        boolean spuNotExists = itemBloomFilterUtil.mightContainSpu(999L);
        log.info("SPU ID 999 是否存在: {}", spuNotExists);
        
        boolean skuNotExists = itemBloomFilterUtil.mightContainSku(999L);
        log.info("SKU ID 999 是否存在: {}", skuNotExists);

        log.info("商品布隆过滤器基本功能测试完成");
    }

    /**
     * 测试商品缓存空对象功能
     */
    @Test
    public void testItemCacheEmptyObject() {
        log.info("开始测试商品缓存空对象功能");

        String testKey = "item:test:cache:empty:1";

        // 第一次查询 - 模拟数据库返回null
        String result1 = itemCacheEmptyUtil.getOrCache(testKey, () -> null, String.class);
        log.info("第一次查询结果: {}", result1);

        // 第二次查询 - 应该从缓存中获取到空值标记
        String result2 = itemCacheEmptyUtil.getOrCache(testKey, () -> {
            log.info("不应该执行到这里");
            return "database_result";
        }, String.class);
        log.info("第二次查询结果: {}", result2);

        // 清理测试缓存
        redisTemplate.delete(testKey);

        log.info("商品缓存空对象功能测试完成");
    }

    /**
     * 测试商品缓存正常数据功能
     */
    @Test
    public void testItemCacheNormalObject() {
        log.info("开始测试商品缓存正常数据功能");

        String testKey = "item:test:cache:normal:1";
        String testData = "test_product_data";

        // 第一次查询 - 模拟数据库返回数据
        String result1 = itemCacheEmptyUtil.getOrCache(testKey, () -> testData, String.class);
        log.info("第一次查询结果: {}", result1);

        // 第二次查询 - 应该从缓存中获取到数据
        String result2 = itemCacheEmptyUtil.getOrCache(testKey, () -> {
            log.info("不应该执行到这里");
            return "other_database_result";
        }, String.class);
        log.info("第二次查询结果: {}", result2);

        // 清理测试缓存
        redisTemplate.delete(testKey);

        log.info("商品缓存正常数据功能测试完成");
    }

    /**
     * 测试商品列表缓存功能
     */
    @Test
    public void testItemCacheList() {
        log.info("开始测试商品列表缓存功能");

        String testKey = "item:test:cache:list:1";
        List<String> testData = Arrays.asList("product1", "product2", "product3");

        // 第一次查询 - 模拟数据库返回列表数据
        List<String> result1 = itemCacheEmptyUtil.getOrCacheList(testKey, () -> testData, String.class);
        log.info("第一次查询结果: {}", result1);

        // 第二次查询 - 应该从缓存中获取到列表数据
        List<String> result2 = itemCacheEmptyUtil.getOrCacheList(testKey, () -> {
            log.info("不应该执行到这里");
            return Arrays.asList("other_product");
        }, String.class);
        log.info("第二次查询结果: {}", result2);

        // 清理测试缓存
        redisTemplate.delete(testKey);

        log.info("商品列表缓存功能测试完成");
    }
}
