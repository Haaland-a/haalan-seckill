package com.haalan.api.client;

import com.haalan.api.domain.vo.UserAddressVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/user/address/inner/{addressId}")
    UserAddressVO getUserAddressById(@RequestParam("addressId") Long addressId,
                                     @RequestParam("userId") Long userId);

    @GetMapping("/api/user/address")
    List<UserAddressVO> getUserAddresses();

    @GetMapping("/api/admin/users/count")
    Long getUserCount();
}
