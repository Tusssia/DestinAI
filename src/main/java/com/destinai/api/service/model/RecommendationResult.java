package com.destinai.api.service.model;

import java.util.List;

/**
 * Service-level recommendation output.
 */
public record RecommendationResult(
		String schemaVersion,
		List<Destination> destinations
) {
}

