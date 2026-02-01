package com.destinai.api.dto.recommendations;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO response for recommendations.
 * Request is authorized via {@code sessions}; response is transient (not persisted).
 */
public record RecommendationResponseDto(
		@JsonProperty("schema_version")
		String schemaVersion,
		List<DestinationDto> destinations
) {
}

