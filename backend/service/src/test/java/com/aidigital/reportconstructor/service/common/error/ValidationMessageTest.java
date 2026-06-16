package com.aidigital.reportconstructor.service.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationMessageTest {

	@Test
	void shouldBeEqualForSameCodeMessageTypeAndParamsTest() {
		// Given / When:
		ValidationMessage a = new ValidationMessage(ErrorReason.C001, 1L);
		ValidationMessage b = new ValidationMessage(ErrorReason.C001, 1L);

		// Then:
		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
		assertThat(a).hasToString("C001: Resource not found: 1");
		assertThat(a.equals(a)).isTrue();
	}

	@Test
	void shouldDifferWhenCodeDiffersTest() {
		// Given / When:
		ValidationMessage a = new ValidationMessage(ErrorReason.C001, 1L);
		ValidationMessage b = new ValidationMessage(ErrorReason.C006, 1L);

		// Then:
		assertThat(a).isNotEqualTo(b);
		assertThat(a.equals(null)).isFalse();
		assertThat(a.equals("not-a-message")).isFalse();
	}

	@Test
	void shouldHandleNullParamsArrayTest() {
		// Given / When:
		ValidationMessage m = new ValidationMessage(ErrorReason.C004, (Object[]) null);

		// Then:
		assertThat(m.getParameters()).isEmpty();
		assertThat(m.getMessage()).isEqualTo("Access forbidden");
		assertThat(m.getCode()).isEqualTo("C004");
	}

	@Test
	void shouldExposeValidationParameterAccessorsAndEqualityTest() {
		// Given:
		ValidationParameter p1 = new ValidationParameter("c", "v");
		ValidationParameter p2 = new ValidationParameter("c", "v");

		// Then:
		assertThat(p1).isEqualTo(p2).hasSameHashCodeAs(p2);
		assertThat(p1.getCode()).isEqualTo("c");
		assertThat(p1.getValue()).isEqualTo("v");
		assertThat(p1).hasToString("c: v");
		assertThat(p1.equals(null)).isFalse();
		assertThat(p1.equals(new ValidationParameter("c", "x"))).isFalse();
	}

	@Test
	void shouldExposeAllMessageTypesTest() {
		// Given / When / Then:
		assertThat(ValidationMessageType.values())
				.containsExactly(ValidationMessageType.ERROR, ValidationMessageType.WARN, ValidationMessageType.INFO);
		assertThat(ValidationMessageType.valueOf("WARN")).isEqualTo(ValidationMessageType.WARN);
	}
}
