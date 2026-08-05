package com.aidigital.reportconstructor.service.admin.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.AdminAccessPolicy;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeUsageEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFailuresServiceImplTest {

	@Mock
	AdminAccessPolicy adminAccessPolicy;

	@Mock
	ReportJobProgressHelper jobs;

	@Mock
	ClaudeUsageEventService usageEvents;

	@InjectMocks
	AdminFailuresServiceImpl service;

	/**
	 * Builds a job with the id, status and warnings under test.
	 *
	 * @param id           the job id
	 * @param status       the job status wire code
	 * @param warningsJson the stored warnings JSON, or {@code null}
	 * @return the job
	 */
	ReportJobEntity job(long id, String status, String warningsJson) {
		ReportJobEntity job = new ReportJobEntity();
		job.setId(id);
		job.setStatus(status);
		job.setWarningsJson(warningsJson);
		return job;
	}

	@Test
	void shouldDeleteAHardFailedJobWithItsUsageEventsTest() {
		// Given: a hard-failed job.
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		when(jobs.findJob(7L)).thenReturn(Optional.of(job(7L, "error", null)));

		// When:
		service.resolveFailure("admin@aidigital.com", 7L);

		// Then: the job and its spend rows are deleted, warnings are not touched.
		verify(usageEvents).deleteByJobId(7L);
		verify(jobs).deleteJob(7L);
		verify(jobs, never()).clearJobWarnings(7L);
	}

	@Test
	void shouldClearOnlyTheWarningsOfADegradedReportTest() {
		// Given: a report that completed but shipped with warnings.
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		when(jobs.findJob(9L)).thenReturn(Optional.of(job(9L, "done", "[\"slide shipped blank\"]")));

		// When:
		service.resolveFailure("admin@aidigital.com", 9L);

		// Then: the report is kept — only its warning flag is cleared.
		verify(jobs).clearJobWarnings(9L);
		verify(jobs, never()).deleteJob(9L);
		verifyNoInteractions(usageEvents);
	}

	@Test
	void shouldRejectANonAdminCallerTest() {
		// Given: a caller who is not on the allow-list.
		when(adminAccessPolicy.isAdmin("intruder@example.com")).thenReturn(false);

		// When-Then: the call is refused and nothing is touched.
		assertThatThrownBy(() -> service.resolveFailure("intruder@example.com", 1L))
				.isInstanceOf(AppException.class);
		verifyNoInteractions(jobs);
		verifyNoInteractions(usageEvents);
	}

	@Test
	void shouldFailWhenTheJobDoesNotExistTest() {
		// Given: no such job.
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		when(jobs.findJob(404L)).thenReturn(Optional.empty());

		// When-Then:
		assertThatThrownBy(() -> service.resolveFailure("admin@aidigital.com", 404L))
				.isInstanceOf(AppException.class);
		verify(jobs, never()).deleteJob(404L);
	}

	@Test
	void shouldClearEveryIssueButLeaveCleanReportsUntouchedTest() {
		// Given: a hard failure and a degraded report. The clean report is deliberately absent —
		// clearing the list reads only the jobs that are issues rather than scanning every job, so a
		// clean report never reaches this code at all.
		when(adminAccessPolicy.isAdmin("admin@aidigital.com")).thenReturn(true);
		ReportJobEntity failed = job(1L, "error", null);
		ReportJobEntity degraded = job(2L, "done", "[\"blank slide\"]");
		when(jobs.listAllIssues()).thenReturn(List.of(failed, degraded));

		// When:
		service.clearFailures("admin@aidigital.com");

		// Then: the failure is deleted with its usage rows, and the degraded report keeps its report
		// but loses its warnings.
		verify(usageEvents).deleteByJobId(1L);
		verify(jobs).deleteJob(1L);
		verify(jobs).clearJobWarnings(2L);
		verify(jobs, never()).deleteJob(2L);
	}
}
