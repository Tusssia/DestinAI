package com.destinai.common.errors;

/**
 * Signals a client error due to invalid input.
 */
public class BadRequestException extends RuntimeException {
	public BadRequestException(String message) {
		super(message);
	}
}

