package com.destinai.unit.recommendations;

import com.destinai.api.command.recommendations.Accommodation;
import com.destinai.api.command.recommendations.Budget;
import com.destinai.api.command.recommendations.Season;
import com.destinai.api.command.recommendations.TravelType;
import com.destinai.api.command.recommendations.Weather;
import com.destinai.api.command.recommendations.Who;
import com.destinai.api.service.recommendations.RecommendationPromptBuilder;
import com.destinai.api.service.recommendations.RecommendationRequest;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationPromptBuilderTest {
	private RecommendationPromptBuilder builder;

	@BeforeEach
	void setUp() {
		builder = new RecommendationPromptBuilder();
	}

	@Test
	void includesSeasonMonthRangeAndConstraintRules() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.WINTER
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("Nov–Feb"));
		Assertions.assertTrue(prompt.contains("Hard constraints"));
		Assertions.assertTrue(prompt.contains("Relaxable constraints"));
		Assertions.assertTrue(prompt.contains("relaxed_constraints"));
	}

	@Test
	void promptContainsAllRequiredJsonSections() {
		RecommendationRequest request = new RecommendationRequest(
				Who.COUPLE,
				TravelType.STAYING_IN_ONE_PLACE,
				Accommodation.HOTELS,
				List.of("beach", "dining"),
				Budget.LUXURIOUS,
				Weather.SUNNY_HUMID,
				Season.SUMMER
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("schema_version"));
		Assertions.assertTrue(prompt.contains("destinations"));
		Assertions.assertTrue(prompt.contains("country"));
		Assertions.assertTrue(prompt.contains("region"));
		Assertions.assertTrue(prompt.contains("estimated_daily_budget_eur_range"));
		Assertions.assertTrue(prompt.contains("best_months"));
		Assertions.assertTrue(prompt.contains("weather_summary"));
		Assertions.assertTrue(prompt.contains("accommodation_fit"));
		Assertions.assertTrue(prompt.contains("travel_style_fit"));
		Assertions.assertTrue(prompt.contains("top_activities"));
		Assertions.assertTrue(prompt.contains("pros"));
		Assertions.assertTrue(prompt.contains("cons"));
		Assertions.assertTrue(prompt.contains("why_match"));
		Assertions.assertTrue(prompt.contains("relaxed_constraints"));
	}

	@Test
	void promptIncludesUserPreferences() {
		RecommendationRequest request = new RecommendationRequest(
				Who.COUPLE,
				TravelType.STAYING_IN_ONE_PLACE,
				Accommodation.HOTELS,
				List.of("museums", "parks"),
				Budget.MEDIUM,
				Weather.COOL,
				Season.SPRING
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("COUPLE"));
		Assertions.assertTrue(prompt.contains("STAYING_IN_ONE_PLACE"));
		Assertions.assertTrue(prompt.contains("HOTELS"));
		Assertions.assertTrue(prompt.contains("museums"));
		Assertions.assertTrue(prompt.contains("parks"));
		Assertions.assertTrue(prompt.contains("MEDIUM"));
		Assertions.assertTrue(prompt.contains("COOL"));
		Assertions.assertTrue(prompt.contains("SPRING"));
	}

	@Test
	void promptIncludesMultipleActivities() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking", "surfing", "photography"),
				Budget.VERY_LOW,
				Weather.SUNNY_DRY,
				Season.AUTUMN
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("hiking"));
		Assertions.assertTrue(prompt.contains("surfing"));
		Assertions.assertTrue(prompt.contains("photography"));
	}

	@Test
	void promptInstructsStrictJsonOnly() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.WINTER
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("STRICT JSON only"));
		Assertions.assertTrue(prompt.contains("exactly 5 destinations"));
		Assertions.assertTrue(prompt.contains("Do not include any text outside JSON"));
	}

	@Test
	void promptIncludesUniquenessConstraints() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.WINTER
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("all 5 countries are unique"));
		Assertions.assertTrue(prompt.contains("no region appears more than 2 times"));
	}

	@Test
	void seasonMonthsMappingForWinter() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.WINTER
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("Nov–Feb"));
	}

	@Test
	void seasonMonthsMappingForSpring() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.SPRING
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("Mar–May"));
	}

	@Test
	void seasonMonthsMappingForSummer() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.SUMMER
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("Jun–Aug"));
	}

	@Test
	void seasonMonthsMappingForAutumn() {
		RecommendationRequest request = new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.AUTUMN
		);

		String prompt = builder.buildPrompt(request);
		Assertions.assertTrue(prompt.contains("Sep–Oct"));
	}

	@Test
	void buildRepairPromptIncludesFailureReason() {
		String failureReason = "duplicate_countries";
		String prompt = builder.buildRepairPrompt(failureReason);

		Assertions.assertTrue(prompt.contains(failureReason));
		Assertions.assertTrue(prompt.contains("STRICT JSON only"));
		Assertions.assertTrue(prompt.contains("exactly 5 destinations"));
	}

	@Test
	void buildRepairPromptIncludesDetailsWhenProvided() {
		String failureReason = "schema_invalid";
		String details = "Missing required field: country";
		String prompt = builder.buildRepairPrompt(failureReason, details);

		Assertions.assertTrue(prompt.contains(failureReason));
		Assertions.assertTrue(prompt.contains("Details: " + details));
	}

	@Test
	void buildRepairPromptHandlesNullDetails() {
		String failureReason = "invalid_json";
		String prompt = builder.buildRepairPrompt(failureReason, null);

		Assertions.assertTrue(prompt.contains(failureReason));
		Assertions.assertFalse(prompt.contains("Details: null"));
	}

	@Test
	void buildRepairPromptIncludesRegionConstraints() {
		String prompt = builder.buildRepairPrompt("region_cap");

		Assertions.assertTrue(prompt.contains("no region appears more than 2 times"));
		Assertions.assertTrue(prompt.contains("Europe"));
		Assertions.assertTrue(prompt.contains("North America"));
		Assertions.assertTrue(prompt.contains("Latin America/Caribbean"));
	}
}

