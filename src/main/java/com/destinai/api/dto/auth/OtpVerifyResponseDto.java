package com.destinai.api.dto.auth;

import com.destinai.api.dto.common.UserDto;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO response for OTP verification.
 * Maps to {@code users} creation/lookup and {@code sessions} creation.
 */
public record OtpVerifyResponseDto(
		@NotBlank
		String status,
		UserDto user
) {
}

