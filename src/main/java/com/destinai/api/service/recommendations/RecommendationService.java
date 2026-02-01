package com.destinai.api.service.recommendations;

import com.destinai.api.dto.recommendations.DestinationDto;
import com.destinai.api.dto.recommendations.RecommendationResponseDto;
import com.destinai.api.service.model.Destination;
import com.destinai.api.service.model.RecommendationResult;
import com.destinai.modules.recommendations.integration.LlmClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class RecommendationService {
	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
	private static final Duration QUICK_RETRY_DELAY = Duration.ofMillis(200);
	private static final int REQUIRED_DESTINATIONS = 5;
	private static final int MAX_REGION_COUNT = 2;
	private static final int MAX_TEXT_LENGTH = 120;
	private static final Set<String> ALLOWED_REGIONS = Set.of(
			"europe",
			"north africa",
			"sub-saharan africa",
			"middle east",
			"south asia",
			"east asia",
			"oceania",
			"north america",
			"latin america/caribbean"
	);

	private final LlmClient llmClient;
	private final RecommendationPromptBuilder promptBuilder;
	private final ObjectMapper objectMapper;

	public RecommendationService(LlmClient llmClient, RecommendationPromptBuilder promptBuilder,
			ObjectMapper objectMapper) {
		this.llmClient = llmClient;
		this.promptBuilder = promptBuilder;
		this.objectMapper = objectMapper;
	}

	public RecommendationResult generate(RecommendationRequest request) {
		String prompt = promptBuilder.buildPrompt(request);
		String response = callWithRetry(prompt);

		ParsedResult parsed = parseResponse(response);
		if (parsed.result() != null) {
			return parsed.result();
		}

		log.warn("LLM validation failed; attempting repair. reason={}", parsed.failure().reason());
		String details = buildRepairDetails(parsed.failure(), parsed.dto(), parsed.rawResponse());
		String repairPrompt = promptBuilder.buildRepairPrompt(parsed.failure().reason(), details);
		String repaired = callWithRetry(repairPrompt);
		ParsedResult repairedResult = parseResponse(repaired);
		if (repairedResult.result() != null) {
			return repairedResult.result();
		}

		log.warn("LLM repair failed. reason={}", repairedResult.failure().reason());
		throw new IllegalStateException("LLM response invalid after repair.");
	}

	private String callWithRetry(String prompt) {
		try {
			return llmClient.complete(prompt);
		} catch (RestClientException ex) {
			log.warn("LLM call failed, retrying once. reason=network_error");
			try {
				Thread.sleep(QUICK_RETRY_DELAY.toMillis());
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
			}
			return llmClient.complete(prompt);
		}
	}

	private ParsedResult parseResponse(String response) {
		try {
			JsonNode payload = objectMapper.readTree(response);
			ValidationFailure schemaFailure = validateSchema(payload);
			if (schemaFailure != null) {
				return new ParsedResult(null, schemaFailure, null, response);
			}
			RecommendationResponseDto dto = objectMapper.treeToValue(payload, RecommendationResponseDto.class);
			ValidationFailure failure = validateBusinessRules(dto);
			if (failure != null) {
				return new ParsedResult(null, failure, dto, response);
			}
			List<List<String>> relaxations = extractRelaxedConstraints(payload);
			List<Destination> destinations = IntStream.range(0, dto.destinations().size())
					.mapToObj(index -> toModel(dto.destinations().get(index), relaxations.get(index)))
					.toList();
			return new ParsedResult(new RecommendationResult(dto.schemaVersion(), destinations), null, dto, response);
		} catch (JsonProcessingException ex) {
			return new ParsedResult(null, new ValidationFailure("invalid_json", "Response was not valid JSON."),
					null, response);
		}
	}

	private ValidationFailure validateSchema(JsonNode payload) {
		if (payload == null || !payload.isObject()) {
			return new ValidationFailure("schema_invalid", "Payload must be a JSON object.");
		}
		if (!payload.hasNonNull("schema_version") || !payload.get("schema_version").isTextual()) {
			return new ValidationFailure("schema_invalid", "schema_version must be a string.");
		}
		JsonNode destinations = payload.get("destinations");
		if (destinations == null || !destinations.isArray()) {
			return new ValidationFailure("schema_invalid", "destinations must be an array.");
		}
		for (JsonNode destination : destinations) {
			if (destination == null || !destination.isObject()) {
				return new ValidationFailure("schema_invalid", "Each destination must be an object.");
			}
			if (!isTextNode(destination, "country")
					|| !isTextNode(destination, "region")
					|| !isTextNode(destination, "estimated_daily_budget_eur_range")
					|| !isTextNode(destination, "weather_summary")
					|| !isTextNode(destination, "accommodation_fit")
					|| !isTextNode(destination, "travel_style_fit")
					|| !isTextNode(destination, "why_match")) {
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isArrayOfText(destination, "best_months")
					|| !isArrayOfText(destination, "top_activities")
					|| !isArrayOfText(destination, "pros")
					|| !isArrayOfText(destination, "cons")) {
				return new ValidationFailure("schema_invalid", "Destination list fields must be arrays of strings.");
			}
			if (destination.has("relaxed_constraints")
					&& !isArrayOfText(destination, "relaxed_constraints")) {
				return new ValidationFailure("schema_invalid", "relaxed_constraints must be an array of strings.");
			}
		}
		return null;
	}

	private ValidationFailure validateBusinessRules(RecommendationResponseDto dto) {
		if (dto.destinations().size() != REQUIRED_DESTINATIONS) {
			return new ValidationFailure("destinations_count", "Expected 5 destinations, got " + dto.destinations().size());
		}
		Map<String, Integer> regionCounts = new HashMap<>();
		Set<String> duplicateCountries = new HashSet<>();
		Set<String> countries = new HashSet<>();
		for (DestinationDto destination : dto.destinations()) {
			String normalized = destination.country().trim().toLowerCase();
			if (!countries.add(normalized)) {
				duplicateCountries.add(destination.country());
				continue;
			}
			String normalizedRegion = destination.region().trim().toLowerCase();
			if (!ALLOWED_REGIONS.contains(normalizedRegion)) {
				return new ValidationFailure("region_invalid", "Invalid region: " + destination.region());
			}
			int count = regionCounts.getOrDefault(normalizedRegion, 0) + 1;
			if (count > MAX_REGION_COUNT) {
				return new ValidationFailure("region_cap", "Region over cap: " + destination.region());
			}
			regionCounts.put(normalizedRegion, count);

			if (isInvalidText(destination.estimatedDailyBudgetEurRange())
					|| isInvalidText(destination.weatherSummary())
					|| isInvalidText(destination.accommodationFit())
					|| isInvalidText(destination.travelStyleFit())
					|| isInvalidText(destination.whyMatch())) {
				return new ValidationFailure("schema_invalid", "Text fields exceed length limits.");
			}

			if (destination.bestMonths() == null || destination.bestMonths().isEmpty()) {
				return new ValidationFailure("schema_invalid", "best_months is empty.");
			}
			if (destination.topActivities() == null || destination.topActivities().isEmpty()
					|| destination.pros() == null || destination.pros().isEmpty()
					|| destination.cons() == null || destination.cons().isEmpty()) {
				return new ValidationFailure("schema_invalid", "List fields must be non-empty.");
			}
			if (containsInvalidListItem(destination.bestMonths())
					|| containsInvalidListItem(destination.topActivities())
					|| containsInvalidListItem(destination.pros())
					|| containsInvalidListItem(destination.cons())) {
				return new ValidationFailure("schema_invalid", "List items exceed length limits.");
			}
		}
		if (!duplicateCountries.isEmpty()) {
			return new ValidationFailure("duplicate_countries", "Duplicates: " + String.join(", ", duplicateCountries));
		}
		return null;
	}

	private boolean isInvalidText(String value) {
		return value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH;
	}

	private boolean containsInvalidListItem(List<String> items) {
		return items.stream().anyMatch(item -> item == null || item.isBlank() || item.length() > MAX_TEXT_LENGTH);
	}

	private Destination toModel(DestinationDto destination, List<String> relaxedConstraints) {
		return new Destination(
				destination.country(),
				destination.region(),
				destination.estimatedDailyBudgetEurRange(),
				destination.bestMonths(),
				destination.weatherSummary(),
				destination.accommodationFit(),
				destination.travelStyleFit(),
				destination.topActivities(),
				destination.pros(),
				destination.cons(),
				destination.whyMatch(),
				relaxedConstraints
		);
	}

	private record ValidationFailure(String reason, String details) {
	}

	private record ParsedResult(RecommendationResult result, ValidationFailure failure,
								RecommendationResponseDto dto, String rawResponse) {
	}

	private boolean isTextNode(JsonNode node, String field) {
		return node.hasNonNull(field) && node.get(field).isTextual();
	}

	private boolean isArrayOfText(JsonNode node, String field) {
		if (!node.hasNonNull(field) || !node.get(field).isArray()) {
			return false;
		}
		for (JsonNode item : node.get(field)) {
			if (item == null || !item.isTextual()) {
				return false;
			}
		}
		return true;
	}

	private List<List<String>> extractRelaxedConstraints(JsonNode payload) {
		JsonNode destinations = payload.get("destinations");
		if (destinations == null || !destinations.isArray()) {
			return List.of();
		}
		return IntStream.range(0, destinations.size())
				.mapToObj(index -> {
					JsonNode destination = destinations.get(index);
					if (destination != null && destination.has("relaxed_constraints")
							&& destination.get("relaxed_constraints").isArray()) {
						return IntStream.range(0, destination.get("relaxed_constraints").size())
								.mapToObj(i -> destination.get("relaxed_constraints").get(i).asText())
								.filter(value -> value != null && !value.isBlank())
								.toList();
					}
					return List.<String>of();
				})
				.toList();
	}

	private String buildRepairDetails(ValidationFailure failure, RecommendationResponseDto dto, String rawResponse) {
		StringBuilder details = new StringBuilder();
		details.append("Failure: ").append(failure.reason()).append(".");
		if (failure.details() != null && !failure.details().isBlank()) {
			details.append(" ").append(failure.details());
		}
		if (dto != null) {
			if ("duplicate_countries".equals(failure.reason())) {
				details.append(" Replace duplicate countries: ")
						.append(String.join(", ", findDuplicates(dto)))
						.append(".");
			}
			if ("region_cap".equals(failure.reason())) {
				details.append(" Replace excess entries in regions over cap: ")
						.append(String.join(", ", findOverrepresented(dto)))
						.append(".");
			}
			if ("destinations_count".equals(failure.reason())) {
				details.append(" Return exactly 5 destinations, add/remove as needed.");
			}
			details.append(" Previous response JSON: ").append(rawResponse);
		} else if (rawResponse != null && !rawResponse.isBlank()) {
			details.append(" Previous response: ").append(rawResponse);
		}
		return details.toString();
	}

	private List<String> findDuplicates(RecommendationResponseDto dto) {
		Set<String> seen = new HashSet<>();
		return dto.destinations().stream()
				.map(DestinationDto::country)
				.filter(country -> {
					String normalized = country == null ? "" : country.trim().toLowerCase();
					if (seen.contains(normalized)) {
						return true;
					}
					seen.add(normalized);
					return false;
				})
				.distinct()
				.toList();
	}

	private List<String> findOverrepresented(RecommendationResponseDto dto) {
		Map<String, Integer> regionCounts = new HashMap<>();
		List<String> replacements = new java.util.ArrayList<>();
		for (DestinationDto destination : dto.destinations()) {
			String normalizedRegion = destination.region().trim().toLowerCase();
			int count = regionCounts.getOrDefault(normalizedRegion, 0) + 1;
			regionCounts.put(normalizedRegion, count);
			if (count > MAX_REGION_COUNT) {
				replacements.add(destination.country());
			}
		}
		return replacements;
	}
}

