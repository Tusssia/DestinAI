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
import org.junit.jupiter.api.Test;

class RecommendationPromptBuilderTest {
	@Test
	void includesSeasonMonthRangeAndConstraintRules() {
		RecommendationPromptBuilder builder = new RecommendationPromptBuilder();
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
}

