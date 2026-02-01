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
class LoginControllerTest {

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
	void loginPageRendersSuccessfully() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(view().name("auth/login"))
				.andExpect(content().contentType("text/html;charset=UTF-8"));
	}

	@Test
	void loginPageContainsEmailInput() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"request-email\"")))
				.andExpect(content().string(containsString("name=\"email\"")))
				.andExpect(content().string(containsString("type=\"email\"")))
				.andExpect(content().string(containsString("required")));
	}

	@Test
	void loginPageContainsOtpRequestForm() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"otp-request-form\"")))
				.andExpect(content().string(containsString("Request a code")))
				.andExpect(content().string(containsString("Send code")));
	}

	@Test
	void loginPageContainsOtpVerifyForm() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"otp-verify-form\"")))
				.andExpect(content().string(containsString("Verify your code")))
				.andExpect(content().string(containsString("id=\"verify-code\"")))
				.andExpect(content().string(containsString("name=\"code\"")))
				.andExpect(content().string(containsString("Verify")));
	}

	@Test
	void loginPageContainsCsrfToken() throws Exception {
		mockMvc.perform(get("/login").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"_csrf_header\"")));
	}

	@Test
	void loginPageHasProperLabels() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("for=\"request-email\"")))
				.andExpect(content().string(containsString("Email address")))
				.andExpect(content().string(containsString("for=\"verify-email\"")))
				.andExpect(content().string(containsString("for=\"verify-code\"")))
				.andExpect(content().string(containsString("One-time code")));
	}

	@Test
	void loginPageHasStatusMessageArea() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"login-status\"")))
				.andExpect(content().string(containsString("role=\"status\"")))
				.andExpect(content().string(containsString("aria-live=\"polite\"")));
	}

	@Test
	void loginPageHasRetryLink() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"retry-request-link\"")))
				.andExpect(content().string(containsString("Request a new code")));
	}

	@Test
	void loginPageHasProperTitle() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>DestinAI - Sign in</title>")));
	}

	@Test
	void loginPageHasProperHeading() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Sign in to DestinAI")))
				.andExpect(content().string(containsString("Get a one-time sign-in code sent to your email")));
	}

	@Test
	void loginPageIncludesLoginScript() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("src=\"/js/login.js\"")));
	}

	@Test
	void rootRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void loginPageHasAutocompleteAttributes() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("autocomplete=\"email\"")))
				.andExpect(content().string(containsString("autocomplete=\"one-time-code\"")));
	}

	@Test
	void loginPageHasNovalidateAttribute() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("novalidate")));
	}

	@Test
	void verifyFormHasHiddenTokenField() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("id=\"verify-token\"")))
				.andExpect(content().string(containsString("name=\"token\"")))
				.andExpect(content().string(containsString("hidden")));
	}
}

