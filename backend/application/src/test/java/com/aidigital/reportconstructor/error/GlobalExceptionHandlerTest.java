package com.aidigital.reportconstructor.error;

import com.aidigital.reportconstructor.api.v1.model.ApiErrorV1;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void shouldMapAppExceptionCodesToHttpStatusesTest() {
		// Given / When / Then: each ErrorReason prefix maps to its HTTP status
		assertThat(status(ErrorReason.C001)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(status(ErrorReason.C004)).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(status(ErrorReason.C005)).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(status(ErrorReason.C006)).isEqualTo(HttpStatus.CONFLICT);
		assertThat(status(ErrorReason.C007)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(status(ErrorReason.C000)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(status(ErrorReason.C002)).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	private HttpStatus status(ErrorReason reason) {
		return (HttpStatus) handler.handleAppException(new AppException(reason, "x")).getStatusCode();
	}

	@Test
	void shouldPopulateApiErrorBodyTest() {
		// When:
		ResponseEntity<ApiErrorV1> resp = handler.handleAppException(new AppException(ErrorReason.C001, 42L));

		// Then:
		ApiErrorV1 body = resp.getBody();
		assertThat(body).isNotNull();
		assertThat(body.getCode()).isEqualTo("C001");
		assertThat(body.getMessage()).contains("Resource not found");
		assertThat(body.getTimestamp()).isNotNull();
		assertThat(body.getParameters()).isNotEmpty();
	}

	@Test
	void shouldMapValidationToBadRequestTest() {
		// When:
		ResponseEntity<ApiErrorV1> resp = handler.handleValidation(new ConstraintViolationException("bad", null));

		// Then:
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp.getBody().getCode()).isEqualTo("C002");
	}

	@Test
	void shouldMapAuthAndAccessDeniedTest() {
		// When / Then:
		assertThat(handler.handleAuth(new BadCredentialsException("nope")).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(handler.handleAccessDenied(new AccessDeniedException("no")).getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void shouldMapUnknownToInternalServerErrorTest() {
		// When:
		ResponseEntity<ApiErrorV1> resp = handler.handleUnknown(new RuntimeException("boom"));

		// Then:
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(resp.getBody().getCode()).isEqualTo("C000");
	}
}
