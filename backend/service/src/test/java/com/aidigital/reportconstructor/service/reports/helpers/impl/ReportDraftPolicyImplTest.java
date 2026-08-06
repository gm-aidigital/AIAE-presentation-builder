package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDraftPolicyImplTest {

	private final ReportDraftPolicyImpl policy = new ReportDraftPolicyImpl();

	/**
	 * Builds one persisted job row.
	 *
	 * @param target   generation target wire code, or null for a legacy row
	 * @param status   lifecycle wire code
	 * @param sheetUrl the workbook the job produced or consumed
	 * @return the job entity
	 */
	ReportJobEntity job(String target, String status, String sheetUrl) {
		ReportJobEntity entity = new ReportJobEntity();
		entity.setTarget(target);
		entity.setStatus(status);
		entity.setSheetUrl(sheetUrl);
		return entity;
	}

	@Test
	void shouldTreatFinishedUnusedSheetBuildAsDraftTest() {
		// Given: a finished sheet build whose workbook no deck was generated from
		ReportJobEntity sheet = job(GenerationTarget.SHEET.name(), "done", "https://sheet/1");

		// When: the draft policy classifies it
		boolean draft = policy.isDraft(sheet, Set.of());

		// Then: it is offered as resumable
		assertThat(draft).isTrue();
		assertThat(policy.isListed(sheet, Set.of())).isTrue();
	}

	@Test
	void shouldNotTreatSheetBuildAsDraftOnceItsWorkbookProducedADeckTest() {
		// Given: a sheet build and a deck run that consumed the same workbook
		ReportJobEntity sheet = job(GenerationTarget.SHEET.name(), "done", "https://sheet/1");
		ReportJobEntity deck = job(GenerationTarget.SLIDES_FROM_SHEET.name(), "done", "https://sheet/1");

		// When: the consumed workbooks are collected over both jobs
		Set<String> consumed = policy.consumedSheetUrls(List.of(deck, sheet));

		// Then: the deck's workbook counts as consumed and the sheet build drops out of the history
		assertThat(consumed).containsExactly("https://sheet/1");
		assertThat(policy.isDraft(sheet, consumed)).isFalse();
		assertThat(policy.isListed(sheet, consumed)).isFalse();
	}

	@Test
	void shouldNotTreatDismissedSheetBuildAsDraftTest() {
		// Given: a finished sheet build the owner dismissed
		ReportJobEntity sheet = job(GenerationTarget.SHEET.name(), "done", "https://sheet/1");
		sheet.setDismissedAt(OffsetDateTime.now());

		// When-Then: it is neither a draft nor listed
		assertThat(policy.isDraft(sheet, Set.of())).isFalse();
		assertThat(policy.isListed(sheet, Set.of())).isFalse();
	}

	@Test
	void shouldNotTreatUnfinishedOrUrllessSheetBuildAsDraftTest() {
		// Given: a sheet build still running, and a failed one that never produced a workbook
		ReportJobEntity running = job(GenerationTarget.SHEET.name(), "running", "https://sheet/1");
		ReportJobEntity failed = job(GenerationTarget.SHEET.name(), "error", null);

		// When-Then: neither has anything to resume
		assertThat(policy.isDraft(running, Set.of())).isFalse();
		assertThat(policy.isDraft(failed, Set.of())).isFalse();
	}

	@Test
	void shouldAlwaysListDeckRunsAndLegacyRowsTest() {
		// Given: a deck run and a legacy row that predates the target column
		ReportJobEntity deck = job(GenerationTarget.SLIDES.name(), "done", null);
		ReportJobEntity legacy = job(null, "error", null);

		// When-Then: both are listed as reports, neither as a draft
		assertThat(policy.isListed(deck, Set.of())).isTrue();
		assertThat(policy.isDraft(deck, Set.of())).isFalse();
		assertThat(policy.isListed(legacy, Set.of())).isTrue();
		assertThat(policy.isDraft(legacy, Set.of())).isFalse();
	}

	@Test
	void shouldNotCountASheetBuildsOwnWorkbookAsConsumedTest() {
		// Given: only sheet builds, each carrying the workbook it produced
		ReportJobEntity sheet = job(GenerationTarget.SHEET.name(), "done", "https://sheet/1");

		// When: the consumed workbooks are collected
		Set<String> consumed = policy.consumedSheetUrls(List.of(sheet));

		// Then: nothing is consumed — a build does not consume its own output
		assertThat(consumed).isEmpty();
		assertThat(policy.isDraft(sheet, consumed)).isTrue();
	}
}
