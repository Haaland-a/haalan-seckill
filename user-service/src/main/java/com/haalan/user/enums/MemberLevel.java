package com.haalan.user.enums;

import lombok.Getter;

@Getter
public enum MemberLevel {
	NORMAL(0, "普通"),
	SILVER(1, "白银"),
	GOLD(2, "黄金"),
	DIAMOND(3, "钻石");

	private final int code;
	private final String desc;

	MemberLevel(int code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public int getCode() {
		return code;
	}
}