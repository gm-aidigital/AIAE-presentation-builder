package com.aidigital.reportconstructor.domain.common.entities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdAwareEntityTest {

	@Test
	void shouldNotEqualTransientEntitiesWithNullIdTest() {
		SampleEntity left = new SampleEntity();
		SampleEntity right = new SampleEntity();
		assertThat(left).isNotEqualTo(right);
		assertThat(left.hashCode()).isZero();
	}

	@Test
	void shouldEqualPersistedEntitiesWithSameIdTest() {
		SampleEntity left = new SampleEntity();
		left.setId(42L);
		SampleEntity right = new SampleEntity();
		right.setId(42L);
		assertThat(left).isEqualTo(right);
		assertThat(left.hashCode()).isEqualTo(right.hashCode());
	}

	private static final class SampleEntity extends IdAwareEntity {

	}
}
