package com.destinai.common.errors;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handling for REST endpoints.
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiErrorDto> handleBadRequest(BadRequestException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorDto("bad_request", ex.getMessage(), null));
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ApiErrorDto> handleUnauthorized(UnauthorizedException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiErrorDto("unauthorized", ex.getMessage(), null));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiErrorDto> handleNotFound(NotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiErrorDto("not_found", ex.getMessage(), null));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorDto> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new HashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorDto("validation_error", "Validation failed.", fieldErrors));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorDto("validation_error", ex.getMessage(), null));
	}

	@ExceptionHandler(LlmValidationException.class)
	public ResponseEntity<ApiErrorDto> handleLlmValidation(LlmValidationException ex) {
		log.warn("LLM validation failed after retries. reason={}", ex.getReasonCode(), ex);
		return ResponseEntity.status(422) // UNPROCESSABLE_ENTITY
				.body(new ApiErrorDto("llm_validation_failed", 
					"Service is temporarily unavailable. Please try again later.", null));
	}

	@ExceptionHandler(LlmServiceException.class)
	public ResponseEntity<ApiErrorDto> handleLlmService(LlmServiceException ex) {
		log.error("LLM service error. reason={}", ex.getReasonCode(), ex);
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ApiErrorDto("llm_service_error", 
					"Service is temporarily unavailable. Please try again later.", null));
	}

	@ExceptionHandler(LlmTimeoutException.class)
	public ResponseEntity<ApiErrorDto> handleLlmTimeout(LlmTimeoutException ex) {
		log.warn("LLM request timed out", ex);
		return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
				.body(new ApiErrorDto("llm_timeout", 
					"Service is temporarily unavailable. Please try again later.", null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorDto> handleUnexpected(Exception ex) {
		log.error("Unhandled API exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiErrorDto("internal_error", "Unexpected server error.", null));
	}
}

