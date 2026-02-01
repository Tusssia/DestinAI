package com.destinai.application.config;

import com.destinai.modules.recommendations.integration.OpenRouterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterConfig {
	@Bean
	public RestClient openRouterRestClient(OpenRouterProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		int timeoutMillis = Math.max(properties.timeoutSeconds(), 1) * 1000;
		requestFactory.setConnectTimeout(timeoutMillis);
		requestFactory.setReadTimeout(timeoutMillis);
		return RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}
}

