package com.destinai.api.dto.favorites;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO response for deleting a favorite.
 * Linked to {@code favorites} deletion by id.
 */
public record FavoriteDeleteResponseDto(
		@NotBlank
		String status
) {
}

