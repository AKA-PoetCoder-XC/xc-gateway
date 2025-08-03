package com.xc.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 配置中心配置信息
 * 
 * @author XieChen
 * @date 2025/08/03
 */
@Slf4j
@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "xc")
public class ApplicationProperties {

	private String remoteConfigName;

	@PostConstruct
	private void postConstruct() {
		log.info("工程配置 ApplicationConfig {}", this);
	}

}