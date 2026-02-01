package com.destinai.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO response for sign out.
 * Linked to {@code sessions} invalidation.
 */
public record LogoutResponseDto(
		@NotBlank
		String status
) {
}

