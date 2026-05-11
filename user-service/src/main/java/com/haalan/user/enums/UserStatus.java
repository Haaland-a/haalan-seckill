package com.haalan.user.enums;


import lombok.Getter;

@Getter
public enum UserStatus {
	DISABLE(0, "禁用"),
	NORMAL(1, "正常");

	private final int code;
	private final String desc;

	UserStatus(int code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public int getCode() {
		return code;
	}
}