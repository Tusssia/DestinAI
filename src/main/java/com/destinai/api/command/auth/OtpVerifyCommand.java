package com.destinai.api.command.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Command model for OTP verification.
 * Maps to {@code otp_tokens.email} and validates against {@code otp_tokens.token_hash}.
 */
public record OtpVerifyCommand(
		@Email
		@NotBlank
		String email,
		String code,
		String token
) {
	@AssertTrue(message = "Either code or token must be provided.")
	public boolean isCodeOrTokenPresent() {
		return (code != null && !code.isBlank()) || (token != null && !token.isBlank());
	}
}

