package com.destinai.api.service.model;

import java.util.UUID;

/**
 * Service-level user model.
 */
public record User(
		UUID id,
		String email
) {
}

