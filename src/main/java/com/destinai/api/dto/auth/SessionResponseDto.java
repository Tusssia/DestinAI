package com.destinai.api.dto.auth;

import com.destinai.api.dto.common.UserDto;

/**
 * DTO response for current session lookup.
 * Derived from {@code sessions} and {@code users}.
 */
public record SessionResponseDto(
		boolean authenticated,
		UserDto user
) {
}

