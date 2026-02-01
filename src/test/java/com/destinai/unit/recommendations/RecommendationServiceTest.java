package com.destinai.unit.recommendations;

import com.destinai.api.service.recommendations.RecommendationPromptBuilder;
import com.destinai.api.service.recommendations.RecommendationRequest;
import com.destinai.api.service.recommendations.RecommendationService;
import com.destinai.common.errors.LlmValidationException;
import com.destinai.modules.recommendations.integration.LlmClient;
import com.destinai.api.command.recommendations.Accommodation;
import com.destinai.api.command.recommendations.Budget;
import com.destinai.api.command.recommendations.Season;
import com.destinai.api.command.recommendations.TravelType;
import com.destinai.api.command.recommendations.Weather;
import com.destinai.api.command.recommendations.Who;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {
	@Test
	void generatesRecommendationsWithRepair() {
		Queue<String> responses = new ArrayDeque<>();
		responses.add("not-json");
		responses.add(validResponse());
		RecommendationService service = new RecommendationService(
				new QueueLlmClient(responses),
				new RecommendationPromptBuilder(),
				new ObjectMapper()
		);

		Assertions.assertEquals(5, service.generate(sampleRequest()).destinations().size());
	}

	@Test
	void failsAfterRepairWhenInvalid() {
		Queue<String> responses = new ArrayDeque<>();
		responses.add(duplicateCountriesResponse());
		responses.add(duplicateCountriesResponse());
		RecommendationService service = new RecommendationService(
				new QueueLlmClient(responses),
				new RecommendationPromptBuilder(),
				new ObjectMapper()
		);

		Assertions.assertThrows(LlmValidationException.class, () -> service.generate(sampleRequest()));
	}

	private RecommendationRequest sampleRequest() {
		return new RecommendationRequest(
				Who.SOLO,
				TravelType.BACKPACKING,
				Accommodation.HOSTELS,
				List.of("hiking", "surfing"),
				Budget.MEDIUM,
				Weather.SUNNY_DRY,
				Season.SUMMER
		);
	}

	private String validResponse() {
		return """
				{
				  "schema_version": "1.0",
				  "destinations": [
				    {
				      "country": "Portugal",
				      "region": "Europe",
				      "estimated_daily_budget_eur_range": "50-100",
				      "best_months": ["May", "June"],
				      "weather_summary": "Sunny and warm.",
				      "accommodation_fit": "Strong",
				      "travel_style_fit": "Strong",
				      "top_activities": ["hiking", "surfing"],
				      "pros": ["Great food"],
				      "cons": ["Crowded summers"],
				      "why_match": "Fits your travel style.",
				      "relaxed_constraints": []
				    },
				    {
				      "country": "Japan",
				      "region": "East Asia",
				      "estimated_daily_budget_eur_range": "120-200",
				      "best_months": ["April", "October"],
				      "weather_summary": "Mild with clear skies.",
				      "accommodation_fit": "Moderate",
				      "travel_style_fit": "Strong",
				      "top_activities": ["hiking", "surfing"],
				      "pros": ["Safe cities"],
				      "cons": ["Higher costs"],
				      "why_match": "Balanced activities.",
				      "relaxed_constraints": ["weather"]
				    },
				    {
				      "country": "Canada",
				      "region": "North America",
				      "estimated_daily_budget_eur_range": "80-150",
				      "best_months": ["June", "July"],
				      "weather_summary": "Cool and sunny.",
				      "accommodation_fit": "Strong",
				      "travel_style_fit": "Moderate",
				      "top_activities": ["hiking", "surfing"],
				      "pros": ["Nature access"],
				      "cons": ["Long distances"],
				      "why_match": "Matches outdoor goals.",
				      "relaxed_constraints": []
				    },
				    {
				      "country": "Chile",
				      "region": "Latin America/Caribbean",
				      "estimated_daily_budget_eur_range": "60-120",
				      "best_months": ["March", "April"],
				      "weather_summary": "Dry and mild.",
				      "accommodation_fit": "Moderate",
				      "travel_style_fit": "Strong",
				      "top_activities": ["hiking", "surfing"],
				      "pros": ["Diverse regions"],
				      "cons": ["Variable weather"],
				      "why_match": "Varied experiences.",
				      "relaxed_constraints": ["travel_type"]
				    },
				    {
				      "country": "New Zealand",
				      "region": "Oceania",
				      "estimated_daily_budget_eur_range": "90-160",
				      "best_months": ["November", "December"],
				      "weather_summary": "Mild and clear.",
				      "accommodation_fit": "Strong",
				      "travel_style_fit": "Strong",
				      "top_activities": ["hiking", "surfing"],
				      "pros": ["Scenic landscapes"],
				      "cons": ["Long travel times"],
				      "why_match": "Great for adventure.",
				      "relaxed_constraints": []
				    }
				  ]
				}
				""";
	}

	private String duplicateCountriesResponse() {
		return """
				{
				  "schema_version": "1.0",
				  "destinations": [
				    {
				      "country": "Portugal",
				      "region": "Europe",
				      "estimated_daily_budget_eur_range": "50-100",
				      "best_months": ["May"],
				      "weather_summary": "Sunny.",
				      "accommodation_fit": "Strong",
				      "travel_style_fit": "Strong",
				      "top_activities": ["hiking"],
				      "pros": ["Great food"],
				      "cons": ["Crowded"],
				      "why_match": "Nice."
				    },
				    {
				      "country": "Portugal",
				      "region": "Europe",
				      "estimated_daily_budget_eur_range": "50-100",
				      "best_months": ["May"],
				      "weather_summary": "Sunny.",
				      "accommodation_fit": "Strong",
				      "travel_style_fit": "Strong",
				      "top_activities": ["hiking"],
				      "pros": ["Great food"],
				      "cons": ["Crowded"],
				      "why_match": "Nice."
				    },
				    {
				      "country": "Japan",
				      "region": "East Asia",
				      "estimated_daily_budget_eur_range": "120-200",
				      "best_months": ["April"],
				      "weather_summary": "Mild.",
				      "accommodation_fit": "Moderate",
				      "travel_style_fit": "Strong",
				      "top_activities": ["culture"],
				      "pros": ["Safe cities"],
				      "cons": ["Higher costs"],
				      "why_match": "Balanced."
				    },
				    {
				      "country": "Canada",
				      "region": "North America",
				      "estimated_daily_budget_eur_range": "80-150",
				      "best_months": ["June"],
				      "weather_summary": "Cool.",
				      "accommodation_fit": "Strong",
				      "travel_style_fit": "Moderate",
				      "top_activities": ["hiking"],
				      "pros": ["Nature"],
				      "cons": ["Distances"],
				      "why_match": "Outdoors."
				    },
				    {
				      "country": "Chile",
				      "region": "Latin America/Caribbean",
				      "estimated_daily_budget_eur_range": "60-120",
				      "best_months": ["March"],
				      "weather_summary": "Dry.",
				      "accommodation_fit": "Moderate",
				      "travel_style_fit": "Strong",
				      "top_activities": ["culture"],
				      "pros": ["Diverse"],
				      "cons": ["Weather"],
				      "why_match": "Varied."
				    }
				  ]
				}
				""";
	}

	private static class QueueLlmClient implements LlmClient {
		private final Queue<String> responses;

		private QueueLlmClient(Queue<String> responses) {
			this.responses = responses;
		}

		@Override
		public String complete(String prompt) {
			return responses.remove();
		}
	}
}

