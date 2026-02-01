package com.destinai.common.errors;

/**
 * Signals that LLM response failed validation after retries.
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class LlmValidationException extends RuntimeException {
	private final String reasonCode;

	public LlmValidationException(String reasonCode, String message) {
		super(message);
		this.reasonCode = reasonCode;
	}

	public LlmValidationException(String reasonCode, String message, Throwable cause) {
		super(message, cause);
		this.reasonCode = reasonCode;
	}

	public String getReasonCode() {
		return reasonCode;
	}
}

