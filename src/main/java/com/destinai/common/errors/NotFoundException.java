package com.destinai.common.errors;

/**
 * Signals a missing resource.
 */
public class NotFoundException extends RuntimeException {
	public NotFoundException(String message) {
		super(message);
	}
}

