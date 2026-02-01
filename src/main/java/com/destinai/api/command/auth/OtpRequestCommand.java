package com.destinai.api.command.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Command model for OTP issuance.
 * Backed by the {@code otp_tokens.email} column and used to locate or create a {@code users} record.
 */
public record OtpRequestCommand(
		@Email
		@NotBlank
		String email
) {
}

