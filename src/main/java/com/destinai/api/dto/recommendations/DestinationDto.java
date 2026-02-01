package com.destinai.api.dto.recommendations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for a recommendation destination.
 * Generated from LLM output; not stored in a database table.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DestinationDto(
		String country,
		String region,
		@JsonProperty("estimated_daily_budget_eur_range")
		String estimatedDailyBudgetEurRange,
		@JsonProperty("best_months")
		List<String> bestMonths,
		@JsonProperty("weather_summary")
		String weatherSummary,
		@JsonProperty("accommodation_fit")
		String accommodationFit,
		@JsonProperty("travel_style_fit")
		String travelStyleFit,
		@JsonProperty("top_activities")
		List<String> topActivities,
		List<String> pros,
		List<String> cons,
		@JsonProperty("why_match")
		String whyMatch
) {
}

