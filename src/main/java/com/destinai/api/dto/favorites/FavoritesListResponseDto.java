package com.destinai.api.dto.favorites;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO response for listing favorites.
 * Backed by {@code favorites} rows scoped to the current {@code sessions.user_id}.
 */
public record FavoritesListResponseDto(
		List<FavoriteDto> items,
		int page,
		@JsonProperty("page_size")
		int pageSize,
		long total
) {
}

