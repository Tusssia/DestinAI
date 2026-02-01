package com.destinai.api.service.recommendations;

import java.util.StringJoiner;
import org.springframework.stereotype.Component;

@Component
public class RecommendationPromptBuilder {
	public String buildPrompt(RecommendationRequest request) {
		StringJoiner activities = new StringJoiner(", ");
		request.activities().forEach(activities::add);
		String seasonMonths = seasonMonths(request.season().name().toLowerCase());

		return """
				You are a travel recommendation engine. Return STRICT JSON only.
				The JSON must have "schema_version" and exactly 5 destinations.
				Do not include any text outside JSON.

				User preferences:
				- who: %s
				- travel_type: %s
				- accommodation: %s
				- activities: %s
				- budget: %s
				- weather: %s
				- season: %s (%s)

				Constraints:
				- Hard constraints (highest priority): who, accommodation, season (month range), budget.
				- Relaxable constraints in order: weather → activities coverage → travel type.
				- Track any relaxations per destination in "relaxed_constraints" (array of strings),
				  but still return 5 destinations.
				- Ensure all 5 countries are unique and no region appears more than 2 times.

				Schema:
				{
				  "schema_version": "1.0",
				  "destinations": [
				    {
				      "country": "string",
				      "region": "string",
				      "estimated_daily_budget_eur_range": "string",
				      "best_months": ["string"],
				      "weather_summary": "string",
				      "accommodation_fit": "string",
				      "travel_style_fit": "string",
				      "top_activities": ["string"],
				      "pros": ["string"],
				      "cons": ["string"],
				      "why_match": "string",
				      "relaxed_constraints": ["string"]
				    }
				  ]
				}
				""".formatted(
				request.who(),
				request.travelType(),
				request.accommodation(),
				activities,
				request.budget(),
				request.weather(),
				request.season(),
				seasonMonths
		);
	}

	public String buildRepairPrompt(String failureReason) {
		return buildRepairPrompt(failureReason, null);
	}

	public String buildRepairPrompt(String failureReason, String details) {
		String extraDetails = details == null || details.isBlank() ? "" : "\nDetails: " + details;
		return """
				The previous response failed validation: %s.%s
				Return STRICT JSON only, matching the required schema and exactly 5 destinations.
				Ensure all countries are unique and no region appears more than 2 times.
				Allowed regions: Europe, North Africa, Sub-Saharan Africa, Middle East, South Asia, East Asia, Oceania,
				North America, Latin America/Caribbean.
				""".formatted(failureReason, extraDetails);
	}

	private String seasonMonths(String season) {
		return switch (season) {
			case "winter" -> "Nov–Feb";
			case "spring" -> "Mar–May";
			case "summer" -> "Jun–Aug";
			case "autumn" -> "Sep–Oct";
			default -> "Unknown";
		};
	}
}

