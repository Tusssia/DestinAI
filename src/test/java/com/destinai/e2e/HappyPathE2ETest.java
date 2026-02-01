package com.destinai.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.destinai.TestcontainersConfiguration;
import com.destinai.modules.auth.integration.OtpSender;
import com.destinai.modules.recommendations.integration.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
class HappyPathE2ETest {

	private static final String SESSION_COOKIE_NAME = "destinai_session";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final CapturingOtpSender otpSender;

	HappyPathE2ETest(MockMvc mockMvc, ObjectMapper objectMapper, CapturingOtpSender otpSender) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.otpSender = otpSender;
	}

	@Test
	void userCanAuthenticateRequestRecommendationsAndManageFavorites() throws Exception {
		mockMvc.perform(post("/api/auth/otp/request")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OtpRequestPayload("happy@example.com"))))
				.andExpect(status().isOk());

		OtpCapture capture = otpSender.latestCapture();

		MvcResult verifyResult = mockMvc.perform(post("/api/auth/otp/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new OtpVerifyPayload(
								"happy@example.com",
								capture.code(),
								null
						))))
				.andExpect(status().isOk())
				.andReturn();

		String setCookieHeader = verifyResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookieHeader).contains(SESSION_COOKIE_NAME);
		Cookie sessionCookie = extractSessionCookie(setCookieHeader);

		mockMvc.perform(post("/api/recommendations")
                        .cookie(sessionCookie)
                        .with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RecommendationPayload(
								"solo",
								"backpacking",
								"hostels",
								List.of("hiking", "surfing"),
								"medium",
								"sunny_dry",
								"summer"
						))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.destinations").isArray())
				.andExpect(jsonPath("$.destinations.length()").value(5));

		mockMvc.perform(post("/api/favorites")
						.cookie(sessionCookie)
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new FavoritePayload("Spain", "Great beaches"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.country").value("Spain"));

		mockMvc.perform(get("/api/favorites")
        .cookie(sessionCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].country").value("Spain"));
	}

    private static Cookie extractSessionCookie(String setCookieHeader) {
		String cookiePair = setCookieHeader.split(";", 2)[0];
		String[] parts = cookiePair.split("=", 2);
		return new Cookie(parts[0], parts.length > 1 ? parts[1] : "");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		@Primary
		LlmClient llmClient() {
			return prompt -> """
					{
					  "schema_version": "2024-09-01",
					  "destinations": [
					    {
					      "country": "Spain",
					      "region": "Western Europe",
					      "estimated_daily_budget_eur_range": "60-120",
					      "best_months": ["May", "June"],
					      "weather_summary": "Warm and sunny with low rainfall.",
					      "accommodation_fit": "Hostels are plentiful and affordable.",
					      "travel_style_fit": "Great for backpacking and public transport.",
					      "top_activities": ["Hiking", "Surfing"],
					      "pros": ["Affordable", "Vibrant culture"],
					      "cons": ["Peak season crowds"],
					      "why_match": "Matches budget and outdoor activities."
					    },
					    {
					      "country": "Portugal",
					      "region": "Western Europe",
					      "estimated_daily_budget_eur_range": "55-110",
					      "best_months": ["June", "July"],
					      "weather_summary": "Sunny coastal weather.",
					      "accommodation_fit": "Hostels and budget hotels are common.",
					      "travel_style_fit": "Easy to backpack with trains.",
					      "top_activities": ["Surfing", "Local cuisine"],
					      "pros": ["Great beaches", "Friendly locals"],
					      "cons": ["Atlantic winds"],
					      "why_match": "Pairs well with sunny weather preference."
					    },
					    {
					      "country": "Croatia",
					      "region": "Southeastern Europe",
					      "estimated_daily_budget_eur_range": "50-100",
					      "best_months": ["June", "September"],
					      "weather_summary": "Clear Adriatic summers.",
					      "accommodation_fit": "Hostels in coastal cities.",
					      "travel_style_fit": "Backpacking friendly islands.",
					      "top_activities": ["Hiking", "Canoeing"],
					      "pros": ["Scenic coast", "Historic towns"],
					      "cons": ["Ferry schedules"],
					      "why_match": "Outdoor activities align with preferences."
					    },
					    {
					      "country": "Greece",
					      "region": "Southern Europe",
					      "estimated_daily_budget_eur_range": "60-130",
					      "best_months": ["May", "September"],
					      "weather_summary": "Hot and dry summer days.",
					      "accommodation_fit": "Hostels on popular islands.",
					      "travel_style_fit": "Island hopping for backpackers.",
					      "top_activities": ["Hiking", "Local culture"],
					      "pros": ["Iconic islands", "Cuisine"],
					      "cons": ["Summer heat"],
					      "why_match": "Sunny climate and cultural activities."
					    },
					    {
					      "country": "Turkey",
					      "region": "Western Asia",
					      "estimated_daily_budget_eur_range": "45-95",
					      "best_months": ["April", "October"],
					      "weather_summary": "Warm with mild evenings.",
					      "accommodation_fit": "Budget stays widely available.",
					      "travel_style_fit": "Backpacking routes are established.",
					      "top_activities": ["Hiking", "Local cuisine"],
					      "pros": ["Great value", "Diverse landscapes"],
					      "cons": ["Long travel distances"],
					      "why_match": "Fits medium budget and activity mix."
					    }
					  ]
					}
					""";
		}

		@Bean
		@Primary
		CapturingOtpSender otpSender() {
			return new CapturingOtpSender();
		}
	}

	static final class CapturingOtpSender implements OtpSender {
		private final AtomicReference<OtpCapture> capture = new AtomicReference<>();

		@Override
		public void sendOtp(String email, String code, String token) {
			capture.set(new OtpCapture(email, code, token));
		}

		OtpCapture latestCapture() {
			OtpCapture latest = capture.get();
			if (latest == null) {
				throw new IllegalStateException("No OTP captured.");
			}
			return latest;
		}
	}

	private record OtpCapture(String email, String code, String token) {
	}

	private record OtpRequestPayload(String email) {
	}

	private record OtpVerifyPayload(String email, String code, String token) {
	}

	private record RecommendationPayload(
			String who,
			String travel_type,
			String accommodation,
			List<String> activities,
			String budget,
			String weather,
			String season
	) {
	}

	private record FavoritePayload(String country, String note) {
	}
}
