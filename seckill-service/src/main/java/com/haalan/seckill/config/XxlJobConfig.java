package com.haalan.seckill.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

	@Value("${xxl.job.admin.addresses}")
	private String adminAddresses;

	@Value("${xxl.job.accessToken}")
	private String accessToken;

	@Value("${xxl.job.executor.appname}")
	private String appname;

	@Value("${xxl.job.executor.port}")
	private int port;

	@Bean
	public XxlJobSpringExecutor xxlJobExecutor() {
		log.info(">>>>>>>>>>> xxl-job config init.");
		XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
		xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
		xxlJobSpringExecutor.setAccessToken(accessToken);
		xxlJobSpringExecutor.setAppname(appname);
		xxlJobSpringExecutor.setPort(port);
		return xxlJobSpringExecutor;
	}
}