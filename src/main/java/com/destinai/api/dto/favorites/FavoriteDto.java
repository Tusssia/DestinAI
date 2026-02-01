package com.destinai.api.dto.favorites;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for a favorite.
 * Derived from {@code favorites} table (id, country, note, created_at).
 */
public record FavoriteDto(
		UUID id,
		String country,
		String note,
		@JsonProperty("created_at")
		Instant createdAt
) {
}

