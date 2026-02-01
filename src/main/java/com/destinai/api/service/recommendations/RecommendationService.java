package com.destinai.api.service.recommendations;

import com.destinai.api.dto.recommendations.DestinationDto;
import com.destinai.api.dto.recommendations.RecommendationResponseDto;
import com.destinai.api.service.model.Destination;
import com.destinai.api.service.model.RecommendationResult;
import com.destinai.common.errors.LlmServiceException;
import com.destinai.common.errors.LlmTimeoutException;
import com.destinai.common.errors.LlmValidationException;
import com.destinai.modules.recommendations.integration.LlmClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class RecommendationService {
	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
	private static final Duration QUICK_RETRY_DELAY = Duration.ofMillis(200);
	private static final int REQUIRED_DESTINATIONS = 5;
	private static final int MAX_REGION_COUNT = 2;
	private static final int MAX_TEXT_LENGTH = 120;

	// Common city/region names that should be rejected (not exhaustive, but catches common cases)
	private static final Set<String> NON_COUNTRY_INDICATORS = Set.of(
			"city", "region", "province", "valley",
			"peninsula", "archipelago", "bay", "gulf", "sea", "ocean"
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
		String response = callWithRetry(prompt, request);

		ParsedResult parsed = parseResponse(response, request);
		if (parsed.result() != null) {
			return parsed.result();
		}

		log.warn("LLM validation failed; attempting repair. reason={}", parsed.failure().reason());
		String details = buildRepairDetails(parsed.failure(), parsed.dto(), parsed.rawResponse(), request);
		String repairPrompt = promptBuilder.buildRepairPrompt(parsed.failure().reason(), details);
		String repaired = callWithRetry(repairPrompt, request);
		ParsedResult repairedResult = parseResponse(repaired, request);
		if (repairedResult.result() != null) {
			return repairedResult.result();
		}

		log.warn("LLM repair failed. reason={}", repairedResult.failure().reason());
		throw new LlmValidationException(repairedResult.failure().reason(),
				"LLM response invalid after repair: " + repairedResult.failure().details());
	}

	private String callWithRetry(String prompt, RecommendationRequest request) {
		try {
			return llmClient.complete(prompt);
		} catch (ResourceAccessException ex) {
			// Timeout or connection issues
			log.warn("LLM call failed (timeout/connection), retrying once. reason=network_error");
			try {
				Thread.sleep(QUICK_RETRY_DELAY.toMillis());
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new LlmTimeoutException("LLM request interrupted", interruptedException);
			}
			try {
				return llmClient.complete(prompt);
			} catch (ResourceAccessException retryEx) {
				log.error("LLM retry failed. reason=timeout");
				throw new LlmTimeoutException("LLM request timed out after retry", retryEx);
			} catch (RestClientResponseException retryEx) {
				log.error("LLM retry failed with HTTP error. status={}, reason=provider_error", retryEx.getStatusCode());
				throw new LlmServiceException("provider_error", "LLM provider returned error: " + retryEx.getStatusCode(), retryEx);
			} catch (RestClientException retryEx) {
				log.error("LLM retry failed. reason=network_error");
				throw new LlmServiceException("network_error", "LLM service unavailable", retryEx);
			}
		} catch (RestClientResponseException ex) {
			// HTTP error from provider
			log.error("LLM provider error. status={}, reason=provider_error", ex.getStatusCode());
			throw new LlmServiceException("provider_error", "LLM provider returned error: " + ex.getStatusCode(), ex);
		} catch (RestClientException ex) {
			// Other network errors
			log.warn("LLM call failed, retrying once. reason=network_error");
			try {
				Thread.sleep(QUICK_RETRY_DELAY.toMillis());
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new LlmServiceException("network_error", "LLM request interrupted", interruptedException);
			}
			try {
				return llmClient.complete(prompt);
			} catch (RestClientException retryEx) {
				log.error("LLM retry failed. reason=network_error");
				throw new LlmServiceException("network_error", "LLM service unavailable after retry", retryEx);
			}
		}
	}

	private ParsedResult parseResponse(String response, RecommendationRequest request) {
		try {
			// Clean response: remove markdown code block markers if present
			String cleanedResponse = cleanJsonResponse(response);
			JsonNode payload = objectMapper.readTree(cleanedResponse);
			ValidationFailure schemaFailure = validateSchema(payload);
			if (schemaFailure != null) {
				log.warn("Schema validation failed. reason={}, details={}", schemaFailure.reason(), schemaFailure.details());
				return new ParsedResult(null, schemaFailure, null, response);
			}
			// Normalize string fields to arrays before converting to DTO
			normalizeArrayFields(payload);
			// Truncate text fields that exceed length limits instead of failing validation
			truncateTextFields(payload);
			// Fill empty arrays with default values instead of rejecting them
			fillEmptyArrays(payload);
			// Parse to DTO after normalization, truncation, and filling
			RecommendationResponseDto dto = objectMapper.treeToValue(payload, RecommendationResponseDto.class);
			ValidationFailure failure = validateBusinessRules(dto, request);
			if (failure != null) {
				log.warn("Business rules validation failed. reason={}, details={}", failure.reason(), failure.details());
				return new ParsedResult(null, failure, dto, response);
			}
			List<List<String>> relaxations = extractRelaxedConstraints(payload);
			List<Destination> destinations = IntStream.range(0, dto.destinations().size())
					.mapToObj(index -> toModel(dto.destinations().get(index), relaxations.get(index)))
					.toList();
			return new ParsedResult(new RecommendationResult(dto.schemaVersion(), destinations), null, dto, response);
		} catch (JsonProcessingException ex) {
			log.warn("JSON parsing failed. error={}, response_preview={}", ex.getMessage(), 
					response != null && response.length() > 200 ? response.substring(0, 200) + "..." : response);
			return new ParsedResult(null, new ValidationFailure("invalid_json", "Response was not valid JSON: " + ex.getMessage()),
					null, response);
		}
	}

	private ValidationFailure validateSchema(JsonNode payload) {
		if (payload == null || !payload.isObject()) {
			log.debug("Schema validation failed: payload is null or not an object");
			return new ValidationFailure("schema_invalid", "Payload must be a JSON object.");
		}
		if (!payload.hasNonNull("schema_version") || !payload.get("schema_version").isTextual()) {
			log.debug("Schema validation failed: schema_version is missing or not a string");
			return new ValidationFailure("schema_invalid", "schema_version must be a string.");
		}
		JsonNode destinations = payload.get("destinations");
		if (destinations == null || !destinations.isArray()) {
			log.debug("Schema validation failed: destinations is missing or not an array");
			return new ValidationFailure("schema_invalid", "destinations must be an array.");
		}
		int destinationIndex = 0;
		for (JsonNode destination : destinations) {
			if (destination == null || !destination.isObject()) {
				log.debug("Schema validation failed: destination at index {} is null or not an object", destinationIndex);
				return new ValidationFailure("schema_invalid", "Each destination must be an object.");
			}
			if (!isTextNode(destination, "country")) {
				log.debug("Schema validation failed: destination[{}].country is missing or not a string", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isTextNode(destination, "region")) {
				log.debug("Schema validation failed: destination[{}].region is missing or not a string", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isTextNode(destination, "estimated_daily_budget_eur_range")) {
				log.debug("Schema validation failed: destination[{}].estimated_daily_budget_eur_range is missing or not a string", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isTextNode(destination, "weather_summary")) {
				log.debug("Schema validation failed: destination[{}].weather_summary is missing or not a string", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isTextNode(destination, "accommodation_fit")) {
				log.debug("Schema validation failed: destination[{}].accommodation_fit is missing or not a string", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isTextNode(destination, "travel_style_fit")) {
				log.debug("Schema validation failed: destination[{}].travel_style_fit is missing or not a string", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isTextNode(destination, "why_match")) {
				log.debug("Schema validation failed: destination[{}].why_match is missing or not a string", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination text fields must be strings.");
			}
			if (!isArrayOfText(destination, "best_months")) {
				log.debug("Schema validation failed: destination[{}].best_months is missing or not an array of strings", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination list fields must be arrays of strings.");
			}
			if (!isArrayOfText(destination, "top_activities")) {
				log.debug("Schema validation failed: destination[{}].top_activities is missing or not an array of strings", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination list fields must be arrays of strings.");
			}
			if (!isArrayOfText(destination, "pros")) {
				log.debug("Schema validation failed: destination[{}].pros is missing or not an array of strings", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination list fields must be arrays of strings.");
			}
			if (!isArrayOfText(destination, "cons")) {
				log.debug("Schema validation failed: destination[{}].cons is missing or not an array of strings", destinationIndex);
				return new ValidationFailure("schema_invalid", "Destination list fields must be arrays of strings.");
			}
			if (destination.has("relaxed_constraints")
					&& !isArrayOfText(destination, "relaxed_constraints")) {
				log.debug("Schema validation failed: destination[{}].relaxed_constraints is not an array of strings", destinationIndex);
				return new ValidationFailure("schema_invalid", "relaxed_constraints must be an array of strings.");
			}
			destinationIndex++;
		}
		return null;
	}

	private ValidationFailure validateBusinessRules(RecommendationResponseDto dto, RecommendationRequest request) {
		if (dto.destinations().size() != REQUIRED_DESTINATIONS) {
			log.debug("Business rules validation failed: Expected {} destinations, got {}", REQUIRED_DESTINATIONS, dto.destinations().size());
			return new ValidationFailure("destinations_count", "Expected 5 destinations, got " + dto.destinations().size());
		}
		Map<String, Integer> regionCounts = new HashMap<>();
		Set<String> duplicateCountries = new HashSet<>();
		Set<String> countries = new HashSet<>();
		Set<String> nonCountryDestinations = new HashSet<>();
		Set<String> activityMismatchDestinations = new HashSet<>();
		
		int destinationIndex = 0;
		for (DestinationDto destination : dto.destinations()) {
			String normalized = destination.country().trim().toLowerCase();
			if (!countries.add(normalized)) {
				log.debug("Business rules validation failed: Duplicate country '{}' at destination index {}", destination.country(), destinationIndex);
				duplicateCountries.add(destination.country());
				continue;
			}
			
			// FR-007: Country-level granularity enforcement
			if (!isValidCountry(destination.country())) {
				log.debug("Business rules validation failed: Non-country destination '{}' at index {}", destination.country(), destinationIndex);
				nonCountryDestinations.add(destination.country());
			}
			
			// FR-014: Activity matching rule - each destination must cover at least 1 selected activity
			// Use flexible matching: check if activity contains the requested activity (case-insensitive)
			int requiredActivityMatches = 1;
			if (destination.topActivities() != null && !destination.topActivities().isEmpty()) {
				long matchingActivities = destination.topActivities().stream()
						.map(String::toLowerCase)
						.filter(activity -> request.activities().stream()
								.map(String::toLowerCase)
								.anyMatch(reqActivity -> activity.contains(reqActivity) || reqActivity.contains(activity)))
						.count();
				if (matchingActivities < requiredActivityMatches) {
					log.debug("Business rules validation failed: Destination '{}' at index {} only covers {} of {} required activities (user selected {}). Activities: {}, Requested: {}", 
							destination.country(), destinationIndex, matchingActivities, requiredActivityMatches, request.activities().size(),
							destination.topActivities(), request.activities());
					activityMismatchDestinations.add(destination.country());
				}
			} else {
				log.debug("Business rules validation failed: Destination '{}' at index {} has no top_activities", destination.country(), destinationIndex);
				activityMismatchDestinations.add(destination.country());
			}
			
			// Normalize region to lowercase for comparison (accept any region name)
			String normalizedRegion = destination.region() == null ? null : destination.region().trim().toLowerCase();
			if (normalizedRegion == null || normalizedRegion.isBlank()) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has empty region", destination.country(), destinationIndex);
				return new ValidationFailure("region_invalid", "Region cannot be empty.");
			}
			int count = regionCounts.getOrDefault(normalizedRegion, 0) + 1;
			if (count > MAX_REGION_COUNT) {
				log.debug("Business rules validation failed: Region '{}' appears {} times (max allowed: {})", destination.region(), count, MAX_REGION_COUNT);
				return new ValidationFailure("region_cap", "Region over cap: " + destination.region());
			}
			regionCounts.put(normalizedRegion, count);

			if (isInvalidText(destination.estimatedDailyBudgetEurRange())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid estimated_daily_budget_eur_range (length: {})", 
						destination.country(), destinationIndex, 
						destination.estimatedDailyBudgetEurRange() != null ? destination.estimatedDailyBudgetEurRange().length() : 0);
				return new ValidationFailure("schema_invalid", "Text fields exceed length limits.");
			}
			if (isInvalidText(destination.weatherSummary())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid weather_summary (length: {})", 
						destination.country(), destinationIndex,
						destination.weatherSummary() != null ? destination.weatherSummary().length() : 0);
				return new ValidationFailure("schema_invalid", "Text fields exceed length limits.");
			}
			if (isInvalidText(destination.accommodationFit())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid accommodation_fit (length: {})", 
						destination.country(), destinationIndex,
						destination.accommodationFit() != null ? destination.accommodationFit().length() : 0);
				return new ValidationFailure("schema_invalid", "Text fields exceed length limits.");
			}
			if (isInvalidText(destination.travelStyleFit())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid travel_style_fit (length: {})", 
						destination.country(), destinationIndex,
						destination.travelStyleFit() != null ? destination.travelStyleFit().length() : 0);
				return new ValidationFailure("schema_invalid", "Text fields exceed length limits.");
			}
			if (isInvalidText(destination.whyMatch())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid why_match (length: {})", 
						destination.country(), destinationIndex,
						destination.whyMatch() != null ? destination.whyMatch().length() : 0);
				return new ValidationFailure("schema_invalid", "Text fields exceed length limits.");
			}

			if (destination.bestMonths() == null || destination.bestMonths().isEmpty()) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has empty best_months", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "best_months is empty.");
			}
			if (destination.topActivities() == null || destination.topActivities().isEmpty()) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has empty top_activities", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "List fields must be non-empty.");
			}
			if (destination.pros() == null || destination.pros().isEmpty()) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has empty pros", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "List fields must be non-empty.");
			}
			if (destination.cons() == null || destination.cons().isEmpty()) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has empty cons", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "List fields must be non-empty.");
			}
			if (containsInvalidListItem(destination.bestMonths())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid item in best_months", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "List items exceed length limits.");
			}
			if (containsInvalidListItem(destination.topActivities())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid item in top_activities", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "List items exceed length limits.");
			}
			if (containsInvalidListItem(destination.pros())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid item in pros", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "List items exceed length limits.");
			}
			if (containsInvalidListItem(destination.cons())) {
				log.debug("Business rules validation failed: Destination '{}' at index {} has invalid item in cons", destination.country(), destinationIndex);
				return new ValidationFailure("schema_invalid", "List items exceed length limits.");
			}
			destinationIndex++;
		}
		if (!duplicateCountries.isEmpty()) {
			log.debug("Business rules validation failed: Duplicate countries found: {}", duplicateCountries);
			return new ValidationFailure("duplicate_countries", "Duplicates: " + String.join(", ", duplicateCountries));
		}
		if (!nonCountryDestinations.isEmpty()) {
			log.debug("Business rules validation failed: Non-country destinations found: {}", nonCountryDestinations);
			return new ValidationFailure("non_country", "Non-country destinations detected: " + String.join(", ", nonCountryDestinations));
		}
		if (!activityMismatchDestinations.isEmpty()) {
			log.debug("Business rules validation failed: Activity coverage issue. Destinations: {}, Requested activities: {}", 
					activityMismatchDestinations, request.activities());
			return new ValidationFailure("activity_coverage", 
					String.format("Destinations must cover at least 1 selected activity: %s", 
							String.join(", ", activityMismatchDestinations)));
		}
		return null;
	}

	/**
	 * FR-007: Validates that the destination is a country, not a city/region.
	 * Uses heuristic: checks if name contains common non-country indicators.
	 */
	private boolean isValidCountry(String countryName) {
		if (countryName == null || countryName.isBlank()) {
			return false;
		}
		String normalized = countryName.trim().toLowerCase();
		// Check if it contains common city/region indicators
		for (String indicator : NON_COUNTRY_INDICATORS) {
			if (normalized.contains(indicator)) {
				return false;
			}
		}
		// Additional heuristic: very short names (< 2 chars) are likely cities
		if (normalized.length() < 2) {
			return false;
		}
		// Common country names are typically 4+ characters (with exceptions)
		// This is a simple heuristic - in production, use a comprehensive country list
		return true;
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

	/**
	 * Cleans the LLM response by extracting JSON from markdown code blocks or text.
	 * Handles cases where the LLM wraps JSON in ```json ... ``` blocks or includes explanatory text.
	 */
	private String cleanJsonResponse(String response) {
		if (response == null) {
			return null;
		}
		String cleaned = response.trim();
		
		// Try to find JSON code block (```json ... ``` or ``` ... ```)
		int codeBlockStart = cleaned.indexOf("```");
		if (codeBlockStart >= 0) {
			// Found a code block, extract content between markers
			int afterStartMarker = cleaned.indexOf('\n', codeBlockStart);
			if (afterStartMarker < 0) {
				afterStartMarker = codeBlockStart + 3; // No newline, skip the ``` itself
			} else {
				afterStartMarker++; // Skip the newline
			}
			int codeBlockEnd = cleaned.lastIndexOf("```");
			if (codeBlockEnd > codeBlockStart) {
				// Extract content between the code block markers
				cleaned = cleaned.substring(afterStartMarker, codeBlockEnd).trim();
			} else {
				// Only opening marker found, extract everything after it
				cleaned = cleaned.substring(afterStartMarker).trim();
			}
		} else {
			// No code block markers, try to find JSON object start
			int jsonStart = cleaned.indexOf('{');
			if (jsonStart > 0) {
				// Found JSON after some text, extract from the opening brace
				cleaned = cleaned.substring(jsonStart);
			}
		}
		
		return cleaned.trim();
	}

	private boolean isTextNode(JsonNode node, String field) {
		return node.hasNonNull(field) && node.get(field).isTextual();
	}

	private boolean isArrayOfText(JsonNode node, String field) {
		if (!node.hasNonNull(field)) {
			return false;
		}
		JsonNode fieldNode = node.get(field);
		// Accept arrays of strings
		if (fieldNode.isArray()) {
			for (JsonNode item : fieldNode) {
				if (item == null || !item.isTextual()) {
					return false;
				}
			}
			return true;
		}
		// Also accept single strings (treat as single-element array)
		if (fieldNode.isTextual()) {
			return true;
		}
		return false;
	}

	/**
	 * Truncates text fields that exceed MAX_TEXT_LENGTH to prevent validation failures.
	 * This makes the validation more forgiving by automatically fixing length issues.
	 */
	private void truncateTextFields(JsonNode payload) {
		JsonNode destinations = payload.get("destinations");
		if (destinations == null || !destinations.isArray()) {
			return;
		}
		for (JsonNode destination : destinations) {
			if (destination == null || !destination.isObject()) {
				continue;
			}
			// Text fields that have length limits
			String[] textFields = {
					"estimated_daily_budget_eur_range",
					"weather_summary",
					"accommodation_fit",
					"travel_style_fit",
					"why_match"
			};
			for (String field : textFields) {
				if (destination.has(field) && destination.get(field).isTextual()) {
					String value = destination.get(field).asText();
					if (value != null && value.length() > MAX_TEXT_LENGTH) {
						String truncated = value.substring(0, MAX_TEXT_LENGTH);
						((ObjectNode) destination).put(field, truncated);
						log.debug("Truncated field '{}' from {} to {} characters", field, value.length(), MAX_TEXT_LENGTH);
					}
				}
			}
			// Also truncate items in array fields
			String[] arrayFields = {"best_months", "top_activities", "pros", "cons", "relaxed_constraints"};
			for (String field : arrayFields) {
				if (destination.has(field) && destination.get(field).isArray()) {
					JsonNode arrayNode = destination.get(field);
					for (int i = 0; i < arrayNode.size(); i++) {
						JsonNode item = arrayNode.get(i);
						if (item != null && item.isTextual()) {
							String value = item.asText();
							if (value != null && value.length() > MAX_TEXT_LENGTH) {
								String truncated = value.substring(0, MAX_TEXT_LENGTH);
								((ArrayNode) arrayNode).set(i, truncated);
								log.debug("Truncated item in array '{}' from {} to {} characters", field, value.length(), MAX_TEXT_LENGTH);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Fills empty arrays with default placeholder values instead of rejecting them.
	 * This makes validation more forgiving by ensuring arrays always have at least one item.
	 */
	private void fillEmptyArrays(JsonNode payload) {
		JsonNode destinations = payload.get("destinations");
		if (destinations == null || !destinations.isArray()) {
			return;
		}
		for (JsonNode destination : destinations) {
			if (destination == null || !destination.isObject()) {
				continue;
			}
			// Fields that must have at least one item
			Map<String, String> requiredArrayFields = Map.of(
					"best_months", "Year-round",
					"top_activities", "General exploration",
					"pros", "Good destination",
					"cons", "Consider your preferences"
			);
			for (Map.Entry<String, String> entry : requiredArrayFields.entrySet()) {
				String field = entry.getKey();
				String defaultValue = entry.getValue();
				if (destination.has(field)) {
					JsonNode fieldNode = destination.get(field);
					if (fieldNode.isArray() && fieldNode.size() == 0) {
						// Fill empty array with default value
						ArrayNode arrayNode = objectMapper.createArrayNode().add(defaultValue);
						((ObjectNode) destination).set(field, arrayNode);
						log.debug("Filled empty array '{}' with default value: {}", field, defaultValue);
					}
				}
			}
		}
	}

	/**
	 * Normalizes string fields that should be arrays by converting them to single-element arrays.
	 * This handles cases where the LLM returns strings instead of arrays for fields like
	 * best_months, pros, and cons.
	 */
	private void normalizeArrayFields(JsonNode payload) {
		JsonNode destinations = payload.get("destinations");
		if (destinations == null || !destinations.isArray()) {
			return;
		}
		for (JsonNode destination : destinations) {
			if (destination == null || !destination.isObject()) {
				continue;
			}
			// Fields that should be arrays but might come as strings
			String[] arrayFields = {"best_months", "pros", "cons", "relaxed_constraints"};
			for (String field : arrayFields) {
				if (destination.has(field)) {
					JsonNode fieldNode = destination.get(field);
				if (fieldNode.isTextual()) {
					// Convert string to single-element array
					((ObjectNode) destination)
							.set(field, objectMapper.createArrayNode().add(fieldNode.asText()));
				}
				}
			}
		}
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

	private String buildRepairDetails(ValidationFailure failure, RecommendationResponseDto dto, String rawResponse, RecommendationRequest request) {
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
			if ("non_country".equals(failure.reason())) {
				details.append(" Replace non-country destinations (cities/regions) with actual countries.");
			}
			if ("activity_coverage".equals(failure.reason())) {
				details.append(" Ensure each destination covers at least 1 of the user's selected activities.");
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

