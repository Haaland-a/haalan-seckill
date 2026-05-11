package com.haalan.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class TUserVO {
	@JsonProperty("userId")
	private Long userId;
	@JsonProperty("username")
	private String username;
}
