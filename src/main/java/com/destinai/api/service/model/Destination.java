package com.destinai.api.service.model;

import java.util.List;

/**
 * Service-level recommendation destination.
 */
public record Destination(
		String country,
		String region,
		String estimatedDailyBudgetEurRange,
		List<String> bestMonths,
		String weatherSummary,
		String accommodationFit,
		String travelStyleFit,
		List<String> topActivities,
		List<String> pros,
		List<String> cons,
		String whyMatch,
		List<String> relaxedConstraints
) {
}

