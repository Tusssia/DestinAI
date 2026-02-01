package com.destinai.api.service.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Service-level favorite model.
 */
public record Favorite(
		UUID id,
		String country,
		String note,
		Instant createdAt
) {
}

