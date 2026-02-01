package com.destinai.api.dto.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * DTO representing a user.
 * Derived from {@code users.id} and {@code users.email}.
 */
public record UserDto(
		UUID id,
		@Email
		@NotBlank
		String email
) {
}

