package com.haalan.item.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * <p>
 * products 索引初始化器
 * </p>
 * <p>
 * 启动时检查 ES 里的 products 索引是否存在，不存在就按下面的 mapping 建好。
 * 索引一旦建好，mapping 就不能再改（ES 的限制），所以必须在这里就把分词器定死。
 * </p>
 * <p>
 * 关于分词器：
 * <ul>
 *   <li>索引时用 ik_max_word —— 切得细，召回高；</li>
 *   <li>查询时用 ik_smart —— 切得粗，避免把「华为手机」拆成无关碎片导致噪声。</li>
 * </ul>
 * 搜索走的是 {@code matchQuery("spuName", keyword)}，它会自动使用字段上配的
 * search_analyzer，所以 Java 查询代码不需要改。
 * </p>
 * <p>
 * 如果服务器上没装 IK 插件（{@code bin/elasticsearch-plugin list} 里没有 analysis-ik），
 * 建索引会失败，此时自动降级用标准分词器建索引并打警告 —— 保证服务不会因为插件缺失而启动不了。
 * </p>
 *
 * @author Haaland
 * @since 2026-10-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexInitializer implements ApplicationRunner {

	/** 商品索引名，item-service 写入、search-service 查询，两边都硬编码这个常量 */
	public static final String PRODUCT_INDEX = "products";

	private final RestHighLevelClient restHighLevelClient;

	/**
	 * <p>
	 * 带 IK 分词器的 mapping。
	 * </p>
	 * <p>
	 * 数值型字段（spuId/categoryId/brandId/status/minPrice/maxPrice/updateTime）保持 long，
	 * 文本型字段保留 keyword 子字段，方便以后做精确匹配与聚合。
	 * </p>
	 */
	private static final String IK_MAPPING = "{\n"
			+ "  \"properties\": {\n"
			+ "    \"spuId\":        {\"type\": \"long\"},\n"
			+ "    \"spuCode\":      {\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},\n"
			+ "    \"spuName\":      {\"type\": \"text\", \"analyzer\": \"ik_max_word\", \"search_analyzer\": \"ik_smart\",\n"
			+ "                       \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},\n"
			+ "    \"description\":  {\"type\": \"text\", \"analyzer\": \"ik_max_word\", \"search_analyzer\": \"ik_smart\"},\n"
			+ "    \"mainImage\":    {\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},\n"
			+ "    \"images\":       {\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},\n"
			+ "    \"categoryId\":   {\"type\": \"long\"},\n"
			+ "    \"categoryName\": {\"type\": \"text\", \"analyzer\": \"ik_max_word\", \"search_analyzer\": \"ik_smart\",\n"
			+ "                       \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},\n"
			+ "    \"brandId\":      {\"type\": \"long\"},\n"
			+ "    \"brandName\":    {\"type\": \"text\", \"analyzer\": \"ik_max_word\", \"search_analyzer\": \"ik_smart\",\n"
			+ "                       \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},\n"
			+ "    \"skuList\":      {\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 256}}},\n"
			+ "    \"status\":        {\"type\": \"long\"},\n"
			+ "    \"minPrice\":     {\"type\": \"long\"},\n"
			+ "    \"maxPrice\":     {\"type\": \"long\"},\n"
			+ "    \"updateTime\":   {\"type\": \"long\"}\n"
			+ "  }\n"
			+ "}";

	/** 没有 IK 插件时的降级 mapping（和上面一样，只是不带 analyzer） */
	private static final String PLAIN_MAPPING = IK_MAPPING
			.replace(", \"analyzer\": \"ik_max_word\", \"search_analyzer\": \"ik_smart\"", "");

	@Override
	public void run(ApplicationArguments args) {
		try {
			GetIndexRequest exists = new GetIndexRequest().indices(PRODUCT_INDEX);
			if (restHighLevelClient.indices().exists(exists, RequestOptions.DEFAULT)) {
				log.info("[ES] 索引 {} 已存在，跳过初始化（mapping 建好后无法修改）", PRODUCT_INDEX);
				return;
			}
			log.info("[ES] 索引 {} 不存在，开始创建（ik_max_word + ik_smart）", PRODUCT_INDEX);
			createIndex(IK_MAPPING);
			log.info("[ES] 索引 {} 创建完成", PRODUCT_INDEX);
		} catch (Exception e) {
			// 建索引失败不能让服务起不来：ES 暂时不可用是常见情况，等 ES 恢复后重启即可
			log.error("[ES] 初始化索引 {} 失败：{}", PRODUCT_INDEX, e.getMessage(), e);
		}
	}

	/**
	 * <p>
	 * 用 IK mapping 建索引；如果报错里提到 analyzer / ik，就用标准分词器再建一次。
	 * </p>
	 *
	 * @param mapping 首次尝试使用的 mapping
	 * @throws Exception 两次都失败时抛出
	 */
	private void createIndex(String mapping) throws Exception {
		try {
			doCreateIndex(mapping);
		} catch (Exception e) {
			String msg = String.valueOf(e.getMessage());
			boolean ikMissing = msg.contains("analyzer") || msg.contains("ik_")
					|| msg.contains("Unknown") || msg.contains("failed to find");
			if (!ikMissing) {
				throw e;
			}
			log.warn("[ES] 用 IK 分词器建索引失败（{}），降级为标准分词器。"
					+ "如果服务器上确实没装 IK 插件，请参考 deploy/README.md 第 6.7 节安装。", msg);
			doCreateIndex(PLAIN_MAPPING);
		}
	}

	private void doCreateIndex(String mapping) throws Exception {
		CreateIndexRequest request = new CreateIndexRequest(PRODUCT_INDEX);
		// 单节点环境副本数设为 0，否则集群会一直是 yellow（副本分片无处分配）
		request.settings(Settings.builder()
				.put("number_of_shards", 1)
				.put("number_of_replicas", 0));
		request.mapping(mapping, XContentType.JSON);
		CreateIndexResponse response = restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
		if (!response.isAcknowledged()) {
			throw new IllegalStateException("建索引未被 ES 确认：" + PRODUCT_INDEX);
		}
	}
}
