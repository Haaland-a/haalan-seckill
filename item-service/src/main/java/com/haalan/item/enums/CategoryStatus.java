package com.haalan.item.enums;


import lombok.Getter;

@Getter
public enum CategoryStatus {
	DISABLE(0, "禁用"),
	NORMAL(1, "正常");

	private final int code;
	private final String desc;

	CategoryStatus(int code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public int getCode() {
		return code;
	}
}