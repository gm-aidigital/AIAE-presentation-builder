package com.aidigital.reportconstructor.externalservices.google;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EomDeckPolicyTest {

	private EomDeckPolicy policy(String eomTemplateId) {
		EomDeckProperties props = new EomDeckProperties();
		props.setSlidesTemplateId(eomTemplateId);
		return new EomDeckPolicy(props);
	}

	@Test
	void unconfiguredEomTemplateKeepsTheEocTemplateForBothReportTypes() {
		EomDeckPolicy policy = policy("");

		assertThat(policy.hasOwnTemplate()).isFalse();
		assertThat(policy.templateIdOr("EOM", "eoc-template")).isEqualTo("eoc-template");
		assertThat(policy.templateIdOr("EOC", "eoc-template")).isEqualTo("eoc-template");
		assertThat(policy.describeTemplate()).isEqualTo("(same as EOC)");
	}

	@Test
	void configuredEomTemplateAppliesToEomDecksOnly() {
		EomDeckPolicy policy = policy("eom-template");

		assertThat(policy.hasOwnTemplate()).isTrue();
		assertThat(policy.templateIdOr("EOM", "eoc-template")).isEqualTo("eom-template");
		assertThat(policy.templateIdOr("EOC", "eoc-template")).isEqualTo("eoc-template");
		assertThat(policy.templateIdOr(null, "eoc-template")).isEqualTo("eoc-template");
		assertThat(policy.describeTemplate()).isEqualTo("eom-template");
	}

	@Test
	void appliesToRecognisesTheEomReportTypeOnly() {
		EomDeckPolicy policy = policy("eom-template");

		assertThat(policy.appliesTo("EOM")).isTrue();
		assertThat(policy.appliesTo("EOC")).isFalse();
		assertThat(policy.appliesTo(null)).isFalse();
	}

	@Test
	void blankAndPaddedTemplateIdsAreNormalised() {
		assertThat(policy("   ").hasOwnTemplate()).isFalse();
		assertThat(policy(null).hasOwnTemplate()).isFalse();
		assertThat(policy("  eom-template  ").templateIdOr("EOM", "eoc-template")).isEqualTo("eom-template");
	}

	@Test
	void dropSlideConfigIsPassedThroughAsConfigured() {
		EomDeckProperties props = new EomDeckProperties();
		props.setDropSlideObjectIds(List.of("id.abc", "def"));
		props.setDropSlideTitles(List.of("DATA SIGNAL"));
		EomDeckPolicy policy = new EomDeckPolicy(props);

		assertThat(policy.dropSlideObjectIds()).containsExactly("id.abc", "def");
		assertThat(policy.dropSlideTitles()).containsExactly("DATA SIGNAL");
	}

	@Test
	void unconfiguredDropListsAreEmptyRatherThanNull() {
		EomDeckPolicy policy = new EomDeckPolicy(new EomDeckProperties());

		assertThat(policy.dropSlideObjectIds()).isEmpty();
		assertThat(policy.dropSlideTitles()).isEmpty();
	}
}
