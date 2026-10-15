package com.haalan.gateway.routers;


import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
//项目开始时执行
public class DynamicRouteLoader {

	private final NacosConfigManager nacosConfigManager;
	private final RouteDefinitionWriter routeDefinitionWriter;
	private final ApplicationEventPublisher publisher;
	private final String dataId = "gateway-routes.json";
	private final String group = "DEFAULT_GROUP";
	private final Set<String> routeIds = new HashSet<>();

	@PostConstruct  //在Bean初始化之后执行
	public void load() throws NacosException {
		log.info("开始加载路由信息");

		//监听路由信息
		String listener = nacosConfigManager.getConfigService()
				.getConfigAndSignListener(dataId, group, 5000, new Listener() {
					@Override
					public Executor getExecutor() {
						return null;
					}

					@Override
					public void receiveConfigInfo(String listener) {

						log.info("监听到路由信息更新：{}", listener);
						updateConfiginfo(listener);
					}
				});
		//第一次读取到路由信息
		updateConfiginfo(listener);
		log.info("加载路由信息完成");
	}

	private void updateConfiginfo(String s) {
		log.info("更新路由信息：{}", s);
		//解析配置文件
		List<RouteDefinition> routeDefinitions = JSONUtil.toList(s, RouteDefinition.class);
		//先删除所有路由
		for (String id : routeIds) {
			routeDefinitionWriter.delete(Mono.just(id)).subscribe();
		}
		routeIds.clear();
		for (RouteDefinition routeDefinition : routeDefinitions) {
			routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();
			routeIds.add(routeDefinition.getId());
		}

		// 通知 Gateway 刷新路由缓存：RouteDefinitionRepository 的写操作不会自动生效，
		// 必须发布 RefreshRoutesEvent 才能让 CachingRouteDefinitionLocator 重新加载，
		// 否则运行期新增/修改的路由在网关重启前不生效（表现为 404）。
		publisher.publishEvent(new RefreshRoutesEvent(this));
		log.info("更新完成，已发布 RefreshRoutesEvent，当前路由：{}", routeIds);

	}
}
