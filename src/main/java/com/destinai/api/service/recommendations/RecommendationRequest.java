package com.destinai.api.service.recommendations;

import com.destinai.api.command.recommendations.Accommodation;
import com.destinai.api.command.recommendations.Budget;
import com.destinai.api.command.recommendations.Season;
import com.destinai.api.command.recommendations.TravelType;
import com.destinai.api.command.recommendations.Weather;
import com.destinai.api.command.recommendations.Who;
import java.util.List;

/**
 * Service-level recommendation request.
 */
public record RecommendationRequest(
		Who who,
		TravelType travelType,
		Accommodation accommodation,
		List<String> activities,
		Budget budget,
		Weather weather,
		Season season
) {
}

