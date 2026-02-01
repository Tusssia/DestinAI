package com.destinai.api.command.favorites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Command model for creating a favorite.
 * Maps to {@code favorites.country} and {@code favorites.note}; {@code favorites.user_id} comes from session.
 */
public record FavoriteCreateCommand(
		@NotBlank
		@Size(max = 100)
		String country,
		@Size(max = 100)
		String note
) {
}

