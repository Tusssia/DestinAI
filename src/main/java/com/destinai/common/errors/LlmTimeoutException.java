package com.destinai.common.errors;

/**
 * Signals that LLM request timed out.
 * Maps to HTTP 504 Gateway Timeout.
 */
public class LlmTimeoutException extends RuntimeException {
	public LlmTimeoutException(String message) {
		super(message);
	}

	public LlmTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}

