package com.haalan.user.domain.vo;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * <p>
 *
 * @author Haaland
 * @description UserInfoVO
 * </p>
 * @date 2026/4/13
 */
@NoArgsConstructor
@Data
public class UserInfoVO {
	@JsonProperty("userId")
	private Long userId;
	@JsonProperty("username")
	private String username;
	@JsonProperty("phone")
	private String phone;
	@JsonProperty("email")
	private String email;
	@JsonProperty("avatar")
	private String avatar;
	@JsonProperty("memberLevel")
	private Integer memberLevel;
	@JsonProperty("lastLoginTime")
	private LocalDateTime lastLoginTime;
	@JsonProperty("lastLoginIp")
	private String lastLoginIp;

	@JsonProperty("avatarUpdateTime")
	private LocalDateTime avatarUpdateTime;


}
