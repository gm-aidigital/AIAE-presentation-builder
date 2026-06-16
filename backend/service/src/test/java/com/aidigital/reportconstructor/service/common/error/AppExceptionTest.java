package com.aidigital.reportconstructor.service.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppExceptionTest {

	@Test
	void shouldFormatMessageFromReasonAndParamsTest() {
		// Given / When:
		AppException ex = new AppException(ErrorReason.C001, 42L);

		// Then:
		assertThat(ex.getCode()).isEqualTo("C001");
		assertThat(ex.getMessage()).isEqualTo("C001: Resource not found: 42");
		assertThat(ex.getLocalizedMessage()).isEqualTo(ex.getMessage());
		assertThat(ex.getValidationMessage().getType()).isEqualTo(ValidationMessageType.ERROR);
		assertThat(ex.getValidationMessage().getParameters()).hasSize(1);
		assertThat(ex.getValidationMessage().getParameters().get(0).getValue()).isEqualTo("42");
	}

	@Test
	void shouldUseDescriptionWhenNoParamsTest() {
		// Given / When:
		AppException ex = new AppException(ErrorReason.C004);

		// Then:
		assertThat(ex.getMessage()).isEqualTo("C004: Access forbidden");
		assertThat(ex.getValidationMessage().getParameters()).isEmpty();
	}

	@Test
	void shouldRetainCauseWhenProvidedTest() {
		// Given:
		Throwable cause = new IllegalStateException("boom");

		// When:
		AppException ex = new AppException(ErrorReason.C006, cause, "dup");

		// Then:
		assertThat(ex.getCause()).isSameAs(cause);
		assertThat(ex.getCode()).isEqualTo("C006");
		assertThat(ex.getMessage()).isEqualTo("C006: Conflict: dup");
	}

	@Test
	void shouldAcceptExplicitValidationParametersTest() {
		// Given:
		ValidationParameter param = new ValidationParameter("sku", "ABC");

		// When:
		AppException ex = new AppException(ErrorReason.C002, param);

		// Then:
		assertThat(ex.getValidationMessage().getParameters()).containsExactly(param);
	}

	@Test
	void shouldAcceptValidationParametersWithCauseTest() {
		// Given:
		Throwable cause = new RuntimeException("x");
		ValidationParameter param = new ValidationParameter("k", "v");

		// When:
		AppException ex = new AppException(ErrorReason.C003, cause, param);

		// Then:
		assertThat(ex.getCause()).isSameAs(cause);
		assertThat(ex.getValidationMessage().getParameters()).containsExactly(param);
	}

	@Test
	void shouldExposeReasonCodeAndDescriptionTest() {
		// Given / When / Then:
		assertThat(ErrorReason.C000.getCode()).isEqualTo("C000");
		assertThat(ErrorReason.C000.getDescription()).contains("Unexpected");
		assertThat(ErrorReason.values()).contains(ErrorReason.C007);
	}
}
