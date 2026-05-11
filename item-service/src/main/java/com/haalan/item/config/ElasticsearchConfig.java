package com.haalan.item.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EsProperties.class)  //获取配置文件
public class ElasticsearchConfig {

	@Bean
	public RestHighLevelClient restHighLevelClient(EsProperties properties) {
		return new RestHighLevelClient(
				RestClient.builder(
						HttpHost.create(properties.getHost())
				)
		);
	}
}