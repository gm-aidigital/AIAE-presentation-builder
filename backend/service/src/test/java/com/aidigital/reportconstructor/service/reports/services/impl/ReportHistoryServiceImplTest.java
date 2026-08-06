package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportResumeStateHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportDraftPolicyImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportHistoryServiceImplTest {

	/**
	 * Builds one persisted job row.
	 *
	 * @param id       surrogate job id
	 * @param target   generation target wire code
	 * @param status   lifecycle wire code
	 * @param sheetUrl the workbook the job produced or consumed
	 * @return the job entity
	 */
	ReportJobEntity job(long id, String target, String status, String sheetUrl) {
		ReportJobEntity entity = new ReportJobEntity();
		entity.setId(id);
		entity.setOwnerUserId("user-1");
		entity.setTarget(target);
		entity.setStatus(status);
		entity.setSheetUrl(sheetUrl);
		return entity;
	}

	/**
	 * Builds a summary stub carrying only the fields the assertions read.
	 *
	 * @param jobId the row's job id
	 * @param draft whether the row is a resumable draft
	 * @return the summary
	 */
	ReportSummary summary(long jobId, boolean draft) {
		return new ReportSummary(jobId, "EOC", "done", "Report", null, null, null, null, null, null,
				null, null, 0, 0, 0, 0, draft);
	}

	@Test
	void shouldListDecksAndUnusedSheetBuildsMarkingTheLatterAsDraftsTest() {
		// Given: a deck, the sheet build it came from, and a second sheet build still unused
		ReportJobEntity deck = job(3, GenerationTarget.SLIDES_FROM_SHEET.name(), "done", "https://sheet/1");
		ReportJobEntity usedSheet = job(2, GenerationTarget.SHEET.name(), "done", "https://sheet/1");
		ReportJobEntity draftSheet = job(1, GenerationTarget.SHEET.name(), "done", "https://sheet/2");
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		ReportSummaryAssembler assembler = mock(ReportSummaryAssembler.class);
		when(jobs.listJobsByOwner("user-1")).thenReturn(List.of(deck, usedSheet, draftSheet));
		when(assembler.toSummary(eq(deck), eq(false))).thenReturn(summary(3, false));
		when(assembler.toSummary(eq(draftSheet), eq(true))).thenReturn(summary(1, true));
		ReportHistoryServiceImpl service = new ReportHistoryServiceImpl(
				jobs, assembler, new ReportDraftPolicyImpl(), mock(ReportResumeStateHelper.class));

		// When: the owner's history is assembled
		List<ReportSummary> rows = service.historyForOwner("user-1");

		// Then: the consumed sheet build is dropped and the unused one is flagged as a draft
		assertThat(rows).extracting(ReportSummary::jobId).containsExactly(3L, 1L);
		assertThat(rows).extracting(ReportSummary::draft).containsExactly(false, true);
		verify(assembler, never()).toSummary(eq(usedSheet), eq(true));
	}

	@Test
	void shouldReturnTheStoredStateWithTheWorkbookWhenResumingADraftTest() {
		// Given: an unused sheet build carrying a stored resume state
		ReportJobEntity draftSheet = job(1, GenerationTarget.SHEET.name(), "done", "https://sheet/2");
		draftSheet.setPayloadJson("{\"reportType\":\"EOM\"}");
		draftSheet.setMediaPlanUrl("https://plan");
		ReportResumeState stored = new ReportResumeState(
				"EOM", "brief", null, null, null, null, null, List.of("Display"));
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		ReportResumeStateHelper resumeState = mock(ReportResumeStateHelper.class);
		when(jobs.loadJobForOwner("user-1", 1L)).thenReturn(draftSheet);
		when(jobs.listJobsByOwner("user-1")).thenReturn(List.of(draftSheet));
		when(resumeState.parse("{\"reportType\":\"EOM\"}")).thenReturn(stored);
		ReportHistoryServiceImpl service = new ReportHistoryServiceImpl(
				jobs, mock(ReportSummaryAssembler.class), new ReportDraftPolicyImpl(), resumeState);

		// When: the draft is resumed
		ReportResume resume = service.resumeForOwner("user-1", 1L);

		// Then: the workbook, the connected sources and the stored state come back together
		assertThat(resume.sheetUrl()).isEqualTo("https://sheet/2");
		assertThat(resume.mediaPlanUrl()).isEqualTo("https://plan");
		assertThat(resume.state()).isSameAs(stored);
	}

	@Test
	void shouldRefuseToResumeAJobThatIsNotADraftTest() {
		// Given: a finished deck, which has nothing left to resume
		ReportJobEntity deck = job(3, GenerationTarget.SLIDES_FROM_SHEET.name(), "done", "https://sheet/1");
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		when(jobs.loadJobForOwner("user-1", 3L)).thenReturn(deck);
		when(jobs.listJobsByOwner("user-1")).thenReturn(List.of(deck));
		ReportHistoryServiceImpl service = new ReportHistoryServiceImpl(
				jobs, mock(ReportSummaryAssembler.class), new ReportDraftPolicyImpl(),
				mock(ReportResumeStateHelper.class));

		// When-Then: resuming it is rejected as not found
		assertThatThrownBy(() -> service.resumeForOwner("user-1", 3L))
				.isInstanceOf(AppException.class)
				.hasMessageContaining("3");
	}

	@Test
	void shouldCheckOwnershipBeforeDismissingTest() {
		// Given: a job the loader will reject as someone else's
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		when(jobs.loadJobForOwner("user-1", 9L)).thenThrow(new IllegalStateException("not yours"));
		ReportHistoryServiceImpl service = new ReportHistoryServiceImpl(
				jobs, mock(ReportSummaryAssembler.class), new ReportDraftPolicyImpl(),
				mock(ReportResumeStateHelper.class));

		// When-Then: the dismissal never reaches the write
		assertThatThrownBy(() -> service.dismissForOwner("user-1", 9L))
				.isInstanceOf(IllegalStateException.class);
		verify(jobs, never()).dismissJob(9L);
	}

	@Test
	void shouldDismissAnOwnedJobTest() {
		// Given: a draft the caller owns
		ReportJobEntity draftSheet = job(1, GenerationTarget.SHEET.name(), "done", "https://sheet/2");
		ReportJobProgressHelper jobs = mock(ReportJobProgressHelper.class);
		when(jobs.loadJobForOwner("user-1", 1L)).thenReturn(draftSheet);
		ReportHistoryServiceImpl service = new ReportHistoryServiceImpl(
				jobs, mock(ReportSummaryAssembler.class), new ReportDraftPolicyImpl(),
				mock(ReportResumeStateHelper.class));

		// When: it is dismissed
		service.dismissForOwner("user-1", 1L);

		// Then: the job is stamped, not deleted
		verify(jobs).dismissJob(1L);
		verify(jobs, never()).deleteJob(1L);
	}
}
