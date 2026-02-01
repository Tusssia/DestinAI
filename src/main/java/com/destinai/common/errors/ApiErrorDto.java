package com.destinai.common.errors;

import java.util.Map;

/**
 * Standard API error response.
 */
public record ApiErrorDto(
		String error,
		String message,
		Map<String, String> fieldErrors
) {
}

