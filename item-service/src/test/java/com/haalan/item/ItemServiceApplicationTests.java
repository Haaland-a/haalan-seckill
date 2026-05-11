package com.haalan.item;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

@SpringBootTest
@ActiveProfiles("local")
class ItemServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@Autowired
	private RestHighLevelClient client;

	@Test  //测试es链接
	public void testConnection() throws IOException {
		System.out.println(client.info(RequestOptions.DEFAULT));
	}


}
