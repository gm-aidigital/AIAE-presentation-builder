// AppException — canonical unchecked exception for ALL business errors.
// Carries a ValidationMessage (code + formatted message + parameters).
// Caught by GlobalExceptionHandler in application/ and converted to the
// OpenAPI ApiErrorV1 response.
//
// Throwing pattern (the only one allowed):
//   throw new AppException(ErrorReason.C001, id);                    // resource not found
//   throw new AppException(ErrorReason.C006, cause, "duplicate sku"); // conflict
//
// Never subclass this. Never throw ResponseStatusException / IllegalStateException
// / IllegalArgumentException from service or controller code — they bypass
// GlobalExceptionHandler's error-code mapping and end up as opaque 500s.

package com.aidigital.reportconstructor.service.common.error;

/**
 * Canonical unchecked exception for business and validation errors.
 */
public class AppException extends RuntimeException {

	private final ValidationMessage validationMessage;

	public AppException(ErrorReason reason, Object... params) {
		this.validationMessage = new ValidationMessage(reason, params);
	}

	public AppException(ErrorReason reason, Throwable cause, Object... params) {
		super(cause);
		this.validationMessage = new ValidationMessage(reason, params);
	}

	public AppException(ErrorReason reason, ValidationParameter... parameters) {
		this.validationMessage = new ValidationMessage(reason, parameters);
	}

	public AppException(ErrorReason reason, Throwable cause, ValidationParameter... parameters) {
		super(cause);
		this.validationMessage = new ValidationMessage(reason, parameters);
	}

	public ValidationMessage getValidationMessage() {
		return validationMessage;
	}

	public String getCode() {
		return validationMessage.getCode();
	}

	@Override
	public String getMessage() {
		return String.valueOf(validationMessage);
	}

	@Override
	public String getLocalizedMessage() {
		return getMessage();
	}
}
