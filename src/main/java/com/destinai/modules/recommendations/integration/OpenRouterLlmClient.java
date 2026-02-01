package com.destinai.modules.recommendations.integration;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenRouterLlmClient implements LlmClient {
	private final RestClient restClient;
	private final OpenRouterProperties properties;

	public OpenRouterLlmClient(RestClient restClient, OpenRouterProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public String complete(String prompt) {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new IllegalStateException("OpenRouter API key is not configured.");
		}
		OpenRouterChatRequest request = new OpenRouterChatRequest(
				properties.model(),
				List.of(new Message("user", prompt))
		);

		try {
			OpenRouterChatResponse response = restClient.post()
					.uri(properties.baseUrl())
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(OpenRouterChatResponse.class);

			if (response == null || response.choices() == null || response.choices().isEmpty()) {
				throw new IllegalStateException("OpenRouter response missing choices.");
			}
			Message message = response.choices().getFirst().message();
			if (message == null || message.content() == null) {
				throw new IllegalStateException("OpenRouter response missing content.");
			}
			return message.content();
		} catch (RestClientException ex) {
			throw ex;
		}
	}

	public record OpenRouterChatRequest(String model, List<Message> messages) {
	}

	public record OpenRouterChatResponse(List<Choice> choices) {
	}

	public record Choice(Message message) {
	}

	public record Message(String role, String content) {
	}
}

