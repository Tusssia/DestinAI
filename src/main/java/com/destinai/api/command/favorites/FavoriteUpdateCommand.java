package com.destinai.api.command.favorites;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Command model for updating a favorite note.
 * Maps to {@code favorites.note} by favorite id.
 */
public record FavoriteUpdateCommand(
		@NotNull
		@Size(max = 100)
		String note
) {
}

