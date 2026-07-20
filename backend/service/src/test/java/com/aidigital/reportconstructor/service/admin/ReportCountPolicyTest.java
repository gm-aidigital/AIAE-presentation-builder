package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCountPolicyTest {

	/**
	 * Builds a report job with the target and type under test.
	 *
	 * @param target         the generation target stored on the job, or {@code null}
	 * @param reportTypeCode the report-type code stored on the job, or {@code null}
	 * @return the job
	 */
	ReportJobEntity job(GenerationTarget target, String reportTypeCode) {
		ReportJobEntity job = new ReportJobEntity();
		job.setTarget(target == null ? null : target.name());
		job.setReportTypeCode(reportTypeCode);
		return job;
	}

	@Test
	void shouldTreatAnEocSheetStepAsAnIntermediateNotAReportTest() {
		// Given: the intermediate review sheet of an EOC run.
		ReportCountPolicy policy = new ReportCountPolicy();
		ReportJobEntity sheet = job(GenerationTarget.SHEET, "EOC");

		// When-Then:
		assertThat(policy.isIntermediateSheet(sheet)).isTrue();
		assertThat(policy.isCountableReport(sheet)).isFalse();
	}

	@Test
	void shouldCountTheDeckStepOfAnEocRunAsAReportTest() {
		// Given: the deck the reviewed sheet fed.
		ReportCountPolicy policy = new ReportCountPolicy();
		ReportJobEntity deck = job(GenerationTarget.SLIDES_FROM_SHEET, "EOC");

		// When-Then:
		assertThat(policy.isCountableReport(deck)).isTrue();
	}

	@Test
	void shouldCountAStandaloneSpreadsheetSheetJobAsAReportTest() {
		// Given: a SHEET job whose type is not a slide-deck type — a standalone spreadsheet deliverable.
		ReportCountPolicy policy = new ReportCountPolicy();
		ReportJobEntity excel = job(GenerationTarget.SHEET, "EXCEL");

		// When-Then: the sheet is the deliverable here, so it counts.
		assertThat(policy.isIntermediateSheet(excel)).isFalse();
		assertThat(policy.isCountableReport(excel)).isTrue();
	}

	@Test
	void shouldMatchTheSlideDeckTypeCaseInsensitivelyTest() {
		// Given: a lower-cased, padded type code as it might arrive from the wire.
		ReportCountPolicy policy = new ReportCountPolicy();
		ReportJobEntity sheet = job(GenerationTarget.SHEET, " eoc ");

		// When-Then:
		assertThat(policy.isIntermediateSheet(sheet)).isTrue();
	}

	@Test
	void shouldCountADirectSlidesJobAsAReportTest() {
		// Given: the one-step "Generate Slides" flow, which never produces an intermediate sheet.
		ReportCountPolicy policy = new ReportCountPolicy();
		ReportJobEntity slides = job(GenerationTarget.SLIDES, "EOC");

		// When-Then:
		assertThat(policy.isCountableReport(slides)).isTrue();
	}
}
