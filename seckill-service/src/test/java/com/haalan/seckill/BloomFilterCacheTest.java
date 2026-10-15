package com.haalan.seckill;

import com.haalan.seckill.util.BloomFilterUtil;
import com.haalan.seckill.util.CacheEmptyUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * 布隆过滤器和缓存空对象测试类
 *
 * @author Haaland
 * @date 2026/5/22
 */
@SpringBootTest
@Slf4j
public class BloomFilterCacheTest {

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    @Autowired
    private CacheEmptyUtil cacheEmptyUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 测试布隆过滤器基本功能
     */
    @Test
    public void testBloomFilter() {
        log.info("开始测试布隆过滤器基本功能");

        // 添加一些测试数据
        List<Long> testIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        bloomFilterUtil.addActivities(testIds);

        // 测试存在的数据
        for (Long id : testIds) {
            boolean exists = bloomFilterUtil.mightContainActivity(id);
            log.info("活动ID {} 是否存在: {}", id, exists);
        }

        // 测试不存在的数据（可能会有误判）
        boolean notExists = bloomFilterUtil.mightContainActivity(999L);
        log.info("活动ID 999 是否存在: {}", notExists);

        log.info("布隆过滤器基本功能测试完成");
    }

    /**
     * 测试缓存空对象功能
     */
    @Test
    public void testCacheEmptyObject() {
        log.info("开始测试缓存空对象功能");

        String testKey = "test:cache:empty:1";

        // 第一次查询 - 模拟数据库返回null
        String result1 = cacheEmptyUtil.getOrCache(testKey, () -> null, String.class);
        log.info("第一次查询结果: {}", result1);

        // 第二次查询 - 应该从缓存中获取到空值标记
        String result2 = cacheEmptyUtil.getOrCache(testKey, () -> {
            log.info("不应该执行到这里");
            return "database_result";
        }, String.class);
        log.info("第二次查询结果: {}", result2);

        // 清理测试缓存
        redisTemplate.delete(testKey);

        log.info("缓存空对象功能测试完成");
    }

    /**
     * 测试缓存正常数据功能
     */
    @Test
    public void testCacheNormalObject() {
        log.info("开始测试缓存正常数据功能");

        String testKey = "test:cache:normal:1";
        String testData = "test_data_value";

        // 第一次查询 - 模拟数据库返回数据
        String result1 = cacheEmptyUtil.getOrCache(testKey, () -> testData, String.class);
        log.info("第一次查询结果: {}", result1);

        // 第二次查询 - 应该从缓存中获取到数据
        String result2 = cacheEmptyUtil.getOrCache(testKey, () -> {
            log.info("不应该执行到这里");
            return "database_result";
        }, String.class);
        log.info("第二次查询结果: {}", result2);

        // 清理测试缓存
        redisTemplate.delete(testKey);

        log.info("缓存正常数据功能测试完成");
    }

    /**
     * 测试列表缓存功能
     */
    @Test
    public void testCacheList() {
        log.info("开始测试列表缓存功能");

        String testKey = "test:cache:list:1";
        List<String> testData = Arrays.asList("item1", "item2", "item3");

        // 第一次查询 - 模拟数据库返回列表数据
        List<String> result1 = cacheEmptyUtil.getOrCacheList(testKey, () -> testData, String.class);
        log.info("第一次查询结果: {}", result1);

        // 第二次查询 - 应该从缓存中获取到列表数据
        List<String> result2 = cacheEmptyUtil.getOrCacheList(testKey, () -> {
            log.info("不应该执行到这里");
            return Arrays.asList("other_item");
        }, String.class);
        log.info("第二次查询结果: {}", result2);

        // 清理测试缓存
        redisTemplate.delete(testKey);

        log.info("列表缓存功能测试完成");
    }
}
