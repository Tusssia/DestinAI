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
class FavoritesControllerTest {

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
	void favoritesPageRendersSuccessfully() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(view().name("favorites/index"))
				.andExpect(content().contentType("text/html;charset=UTF-8"));
	}

	@Test
	void favoritesPageContainsCsrfToken() throws Exception {
		mockMvc.perform(get("/favorites").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"_csrf_header\"")));
	}

	@Test
	void favoritesPageHasProperTitle() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>DestinAI - Favorites</title>")));
	}

	@Test
	void favoritesPageHasProperHeading() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Favorites")));
	}

	@Test
	void favoritesPageHasNavigationLinks() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/questionnaire\"")))
				.andExpect(content().string(containsString("New destination")))
				.andExpect(content().string(containsString("id=\"logout-button\"")));
	}

	@Test
	void favoritesPageHasSearchForm() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"favorites-search-form\"")))
				.andExpect(content().string(containsString("id=\"favorites-search\"")))
				.andExpect(content().string(containsString("name=\"country\"")))
				.andExpect(content().string(containsString("type=\"search\"")))
				.andExpect(content().string(containsString("Search by country")));
	}

	@Test
	void favoritesPageHasSortSelect() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"favorites-sort\"")))
				.andExpect(content().string(containsString("name=\"sort\"")))
				.andExpect(content().string(containsString("value=\"created_at_desc\"")))
				.andExpect(content().string(containsString("value=\"created_at_asc\"")))
				.andExpect(content().string(containsString("Newest first")))
				.andExpect(content().string(containsString("Oldest first")));
	}

	@Test
	void favoritesPageHasStatusMessageArea() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"favorites-status\"")))
				.andExpect(content().string(containsString("role=\"status\"")))
				.andExpect(content().string(containsString("aria-live=\"polite\"")));
	}

	@Test
	void favoritesPageHasEmptyState() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"favorites-empty\"")))
				.andExpect(content().string(containsString("You have no saved destinations yet")))
				.andExpect(content().string(containsString("Find a new destination")));
	}

	@Test
	void favoritesPageHasFavoritesListContainer() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"favorites-list\"")));
	}

	@Test
	void favoritesPageHasPagination() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("favorites-pagination")))
				.andExpect(content().string(containsString("id=\"favorites-prev\"")))
				.andExpect(content().string(containsString("id=\"favorites-next\"")))
				.andExpect(content().string(containsString("id=\"favorites-page-info\"")))
				.andExpect(content().string(containsString("aria-label=\"Favorites pagination\"")));
	}

	@Test
	void favoritesPageIncludesFavoritesScript() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("src=\"/js/favorites.js\"")));
	}

	@Test
	void favoritesPageHasProperLabels() throws Exception {
		mockMvc.perform(get("/favorites"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("for=\"favorites-search\"")))
				.andExpect(content().string(containsString("for=\"favorites-sort\"")));
	}
}

