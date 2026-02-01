package com.destinai.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO response for OTP request.
 * Linked to {@code otp_tokens} issuance result.
 */
public record OtpRequestResponseDto(
		@NotBlank
		String status
) {
}

