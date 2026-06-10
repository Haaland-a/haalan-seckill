package com.haalan.api.client.fallback;

import com.haalan.api.client.UserServiceClient;
import com.haalan.api.domain.vo.UserAddressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class UserServiceClientFallback implements FallbackFactory<UserServiceClient> {

	@Override
	public UserServiceClient create(Throwable cause) {
		return new UserServiceClient() {

			@Override
			public UserAddressVO getUserAddressById(Long addressId, Long userId) {
				log.error("调用 user-service 获取地址失败, addressId: {}, userId: {}", addressId, userId, cause);
				return null;
			}

			@Override
			public List<UserAddressVO> getUserAddresses() {
				log.error("调用 user-service 获取地址列表失败", cause);
				return List.of();
			}

			@Override
			public Long getUserCount() {
				log.error("调用 user-service 获取用户数量失败", cause);
				return 0L;
			}
		};
	}
}
