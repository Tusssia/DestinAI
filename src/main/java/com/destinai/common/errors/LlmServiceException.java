package com.destinai.common.errors;

/**
 * Signals that LLM provider service is unavailable or returned an error.
 * Maps to HTTP 502 Bad Gateway.
 */
public class LlmServiceException extends RuntimeException {
	private final String reasonCode;

	public LlmServiceException(String reasonCode, String message) {
		super(message);
		this.reasonCode = reasonCode;
	}

	public LlmServiceException(String reasonCode, String message, Throwable cause) {
		super(message, cause);
		this.reasonCode = reasonCode;
	}

	public String getReasonCode() {
		return reasonCode;
	}
}

