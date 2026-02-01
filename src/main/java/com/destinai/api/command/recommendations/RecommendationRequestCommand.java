package com.destinai.api.command.recommendations;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Command model for generating recommendations.
 * The request is authenticated via {@code sessions}, but the payload itself is not persisted.
 */
public record RecommendationRequestCommand(
		@NotNull
		Who who,
		@JsonProperty("travel_type")
		@NotNull
		TravelType travelType,
		@NotNull
		Accommodation accommodation,
		@NotEmpty
		List<@NotBlank String> activities,
		@NotNull
		Budget budget,
		@NotNull
		Weather weather,
		@NotNull
		Season season
) {
}

