package com.destinai.api.web;

import com.destinai.api.command.auth.OtpRequestCommand;
import com.destinai.api.command.auth.OtpVerifyCommand;
import com.destinai.api.dto.auth.LogoutResponseDto;
import com.destinai.api.dto.auth.OtpRequestResponseDto;
import com.destinai.api.dto.auth.OtpVerifyResponseDto;
import com.destinai.api.dto.auth.SessionResponseDto;
import com.destinai.api.dto.common.UserDto;
import com.destinai.api.service.auth.AuthService;
import com.destinai.api.service.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {
	private static final String SESSION_COOKIE_NAME = "destinai_session";
	private static final Duration SESSION_TTL = Duration.ofDays(30);

	private final AuthService authService;

	public AuthApiController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/otp/request")
	public OtpRequestResponseDto requestOtp(@Valid @RequestBody OtpRequestCommand command,
			HttpServletRequest request) {
		authService.requestOtp(command.email(), resolveClientIp(request));
		return new OtpRequestResponseDto("sent");
	}

	@PostMapping("/otp/verify")
	public ResponseEntity<OtpVerifyResponseDto> verifyOtp(@Valid @RequestBody OtpVerifyCommand command) {
		AuthService.AuthResult result = authService.verifyOtp(command.email(), command.code(), command.token());
		ResponseCookie cookie = buildSessionCookie(result.sessionToken());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new OtpVerifyResponseDto("authenticated", toUserDto(result.user())));
	}

	@PostMapping("/logout")
	public ResponseEntity<LogoutResponseDto> logout(
			@CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionToken
	) {
		authService.logout(sessionToken);
		ResponseCookie cookie = clearSessionCookie();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(new LogoutResponseDto("logged_out"));
	}

	@GetMapping("/session")
	public SessionResponseDto session(
			@CookieValue(name = SESSION_COOKIE_NAME, required = false) String sessionToken
	) {
		Optional<User> user = authService.getUserForSession(sessionToken);
		return new SessionResponseDto(user.isPresent(), user.map(this::toUserDto).orElse(null));
	}

	private ResponseCookie buildSessionCookie(String sessionToken) {
		return ResponseCookie.from(SESSION_COOKIE_NAME, sessionToken)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(SESSION_TTL)
				.build();
	}

	private ResponseCookie clearSessionCookie() {
		return ResponseCookie.from(SESSION_COOKIE_NAME, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ZERO)
				.build();
	}

	private UserDto toUserDto(User user) {
		return new UserDto(user.id(), user.email());
	}

	private String resolveClientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}

