package com.destinai.modules.recommendations.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
		String baseUrl,
		String model,
		String apiKey,
		int timeoutSeconds
) {
}

