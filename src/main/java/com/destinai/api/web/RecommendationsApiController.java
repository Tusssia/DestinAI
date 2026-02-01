package com.destinai.api.web;

import com.destinai.api.command.recommendations.RecommendationRequestCommand;
import com.destinai.api.dto.recommendations.DestinationDto;
import com.destinai.api.dto.recommendations.RecommendationResponseDto;
import com.destinai.api.service.auth.AuthService;
import com.destinai.api.service.model.Destination;
import com.destinai.api.service.model.RecommendationResult;
import com.destinai.api.service.recommendations.RecommendationRequest;
import com.destinai.api.service.recommendations.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationsApiController {
	private static final String SESSION_COOKIE_NAME = "destinai_session";

	private final AuthService authService;
	private final RecommendationService recommendationService;

	public RecommendationsApiController(AuthService authService, RecommendationService recommendationService) {
		this.authService = authService;
		this.recommendationService = recommendationService;
	}

	@PostMapping
	public RecommendationResponseDto recommend(
			@CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionToken,
			@Valid @RequestBody RecommendationRequestCommand command
	) {
		authService.requireUser(sessionToken);
		RecommendationRequest request = new RecommendationRequest(
				command.who(),
				command.travelType(),
				command.accommodation(),
				command.activities(),
				command.budget(),
				command.weather(),
				command.season()
		);
		RecommendationResult result = recommendationService.generate(request);
		return new RecommendationResponseDto(
				result.schemaVersion(),
				result.destinations().stream().map(this::toDto).toList()
		);
	}

	private DestinationDto toDto(Destination destination) {
		return new DestinationDto(
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
				destination.whyMatch()
		);
	}
}

