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
class ResultsControllerTest {

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
	void resultsPageRendersSuccessfully() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(view().name("recommendations/results"))
				.andExpect(content().contentType("text/html;charset=UTF-8"));
	}

	@Test
	void resultsPageContainsCsrfToken() throws Exception {
		mockMvc.perform(get("/results").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"_csrf_header\"")));
	}

	@Test
	void resultsPageHasProperTitle() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>DestinAI - Results</title>")));
	}

	@Test
	void resultsPageHasProperHeading() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Your recommendations")))
				.andExpect(content().string(containsString("Here are five destinations that match your preferences")));
	}

	@Test
	void resultsPageHasNavigationLinks() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/favorites\"")))
				.andExpect(content().string(containsString("Favorites")))
				.andExpect(content().string(containsString("href=\"/questionnaire\"")))
				.andExpect(content().string(containsString("New destination")));
	}

	@Test
	void resultsPageHasLoadingState() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"results-loading\"")))
				.andExpect(content().string(containsString("hidden")))
				.andExpect(content().string(containsString("Loading recommendations")));
	}

	@Test
	void resultsPageHasErrorState() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"results-error\"")))
				.andExpect(content().string(containsString("hidden")))
				.andExpect(content().string(containsString("id=\"results-error-message\"")))
				.andExpect(content().string(containsString("Something went wrong")))
				.andExpect(content().string(containsString("id=\"results-retry\"")))
				.andExpect(content().string(containsString("Retry")));
	}

	@Test
	void resultsPageHasResultsListContainer() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"results-list\"")));
	}

	@Test
	void resultsPageIncludesResultsScript() throws Exception {
		mockMvc.perform(get("/results"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("src=\"/js/results.js\"")));
	}
}

