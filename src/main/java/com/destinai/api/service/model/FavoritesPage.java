package com.destinai.api.service.model;

import java.util.List;

/**
 * Paged favorites result.
 */
public record FavoritesPage(
		List<Favorite> items,
		int page,
		int pageSize,
		long total
) {
}

