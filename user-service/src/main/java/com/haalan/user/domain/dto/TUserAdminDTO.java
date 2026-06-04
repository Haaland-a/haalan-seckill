package com.haalan.user.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class TUserAdminDTO extends TUserDTO {
	//管理员端专属参数
	@JsonProperty("admin")
	private String admin;
}
