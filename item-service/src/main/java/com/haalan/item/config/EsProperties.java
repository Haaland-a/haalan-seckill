package com.haalan.item.config;

/**
 * <p>
 *
 * @author Haaland
 * @description EsProperties
 * </p>
 * @date 2026/4/16
 */

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "haalan.es")
public class EsProperties {
	private String host;
}
