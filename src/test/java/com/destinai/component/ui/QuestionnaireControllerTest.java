package com.destinai.component.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
		"spring.flyway.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:testdb",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class QuestionnaireControllerTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
				.webAppContextSetup(webApplicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void questionnairePageRendersSuccessfully() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(view().name("questionnaire/questionnaire"))
				.andExpect(content().contentType("text/html;charset=UTF-8"));
	}

	@Test
	void questionnairePageContainsCsrfToken() throws Exception {
		mockMvc.perform(get("/questionnaire").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"_csrf_header\"")));
	}

	@Test
	void questionnairePageHasProperTitle() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>DestinAI - Questionnaire</title>")));
	}

	@Test
	void questionnairePageHasProperHeading() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Tell us about your trip")))
				.andExpect(content().string(containsString("Answer all questions to generate your recommendations")));
	}

	@Test
	void questionnairePageContainsWhoFieldset() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Who are you traveling with?")))
				.andExpect(content().string(containsString("name=\"who\"")))
				.andExpect(content().string(containsString("value=\"solo\"")))
				.andExpect(content().string(containsString("value=\"couple\"")));
	}

	@Test
	void questionnairePageContainsTravelTypeFieldset() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("What kind of trip is this?")))
				.andExpect(content().string(containsString("name=\"travel_type\"")))
				.andExpect(content().string(containsString("value=\"backpacking\"")))
				.andExpect(content().string(containsString("value=\"staying_in_one_place\"")));
	}

	@Test
	void questionnairePageContainsAccommodationFieldset() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Preferred accommodation")))
				.andExpect(content().string(containsString("name=\"accommodation\"")))
				.andExpect(content().string(containsString("value=\"camping\"")))
				.andExpect(content().string(containsString("value=\"hostels\"")))
				.andExpect(content().string(containsString("value=\"hotels\"")));
	}

	@Test
	void questionnairePageContainsActivitiesFieldset() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Favorite activities (choose at least one)")))
				.andExpect(content().string(containsString("name=\"activities\"")))
				.andExpect(content().string(containsString("type=\"checkbox\"")))
				.andExpect(content().string(containsString("value=\"hiking\"")))
				.andExpect(content().string(containsString("value=\"surfing\"")));
	}

	@Test
	void questionnairePageContainsBudgetFieldset() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Budget")))
				.andExpect(content().string(containsString("name=\"budget\"")))
				.andExpect(content().string(containsString("value=\"very_low\"")))
				.andExpect(content().string(containsString("value=\"medium\"")))
				.andExpect(content().string(containsString("value=\"luxurious\"")));
	}

	@Test
	void questionnairePageContainsWeatherFieldset() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Weather preference")))
				.andExpect(content().string(containsString("name=\"weather\"")))
				.andExpect(content().string(containsString("value=\"sunny_dry\"")))
				.andExpect(content().string(containsString("value=\"sunny_humid\"")))
				.andExpect(content().string(containsString("value=\"cool\"")))
				.andExpect(content().string(containsString("value=\"rainy\"")));
	}

	@Test
	void questionnairePageContainsSeasonFieldset() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Preferred season")))
				.andExpect(content().string(containsString("name=\"season\"")))
				.andExpect(content().string(containsString("value=\"winter\"")))
				.andExpect(content().string(containsString("value=\"spring\"")))
				.andExpect(content().string(containsString("value=\"summer\"")))
				.andExpect(content().string(containsString("value=\"autumn\"")));
	}

	@Test
	void questionnairePageHasSubmitButton() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"questionnaire-submit\"")))
				.andExpect(content().string(containsString("type=\"submit\"")))
				.andExpect(content().string(containsString("Get recommendations")))
				.andExpect(content().string(containsString("disabled")));
	}

	@Test
	void questionnairePageHasStatusMessageArea() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"questionnaire-status\"")))
				.andExpect(content().string(containsString("role=\"status\"")))
				.andExpect(content().string(containsString("aria-live=\"polite\"")));
	}

	@Test
	void questionnairePageHasFormWithNovalidate() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"questionnaire-form\"")))
				.andExpect(content().string(containsString("novalidate")));
	}

	@Test
	void questionnairePageIncludesQuestionnaireScript() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("src=\"/js/questionnaire.js\"")));
	}

	@Test
	void questionnairePageHasNavigationLinks() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/favorites\"")))
				.andExpect(content().string(containsString("Home")))
				.andExpect(content().string(containsString("id=\"logout-button\"")));
	}

	@Test
	void questionnairePageUsesFieldsetAndLegend() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<fieldset>")))
				.andExpect(content().string(containsString("<legend>")));
	}

	@Test
	void questionnairePageHasProperRadioInputs() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("type=\"radio\"")));
	}

	@Test
	void questionnairePageHasProperCheckboxInputs() throws Exception {
		mockMvc.perform(get("/questionnaire"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("type=\"checkbox\"")));
	}
}

