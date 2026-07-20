package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminFailedJob;
import com.aidigital.reportconstructor.service.common.text.DisplayNameHelper;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSummaryAssembler;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import com.aidigital.reportconstructor.service.reports.usage.JobTokenUsage;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudeModelPrice;
import com.aidigital.reportconstructor.service.reports.usage.config.ClaudePricingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFailureAssemblerTest {

	@Mock
	ReportSummaryAssembler summaryAssembler;

	/**
	 * Builds the assembler under test with real (cheap) token collaborators.
	 *
	 * @return the assembler
	 */
	AdminFailureAssembler assembler() {
		ClaudePricingProperties pricing = new ClaudePricingProperties();
		pricing.setDefaultPrice(new ClaudeModelPrice());
		return new AdminFailureAssembler(
				new DisplayNameHelper(), summaryAssembler, new JobTokenUsage(new ClaudeCostCalculator(pricing)));
	}

	/**
	 * Stubs the summary assembler to return a row whose title is {@code title}.
	 *
	 * @param title the title the failure row should pick up
	 */
	void stubTitle(String title) {
		when(summaryAssembler.toSummary(org.mockito.ArgumentMatchers.any(ReportJobEntity.class)))
				.thenReturn(new ReportSummary(1L, "EOC", "error", title, null, null, null, null, null, null,
						null, null, 0, 0, 0, 0d));
	}

	@Test
	void shouldCarryTheStepTheJobDiedOnTest() {
		// Given: a job that failed on step 5, with the step label the pipeline last stamped on it.
		ReportJobEntity job = new ReportJobEntity();
		job.setStatus("error");
		job.setStep(5);
		job.setTotal(7);
		job.setLabel("Claude — executive batch (C)");
		job.setErrorMessage("Read timed out");
		job.setOwnerEmail("jane.doe@aidigital.com");
		job.setCreatedAt(OffsetDateTime.now());
		job.setUpdatedAt(OffsetDateTime.now());
		stubTitle("Q3 EOC deck");

		// When:
		List<AdminFailedJob> failures = assembler().recentFailures(List.of(job), 10);

		// Then:
		assertThat(failures).hasSize(1);
		assertThat(failures.getFirst().step()).isEqualTo(5);
		assertThat(failures.getFirst().total()).isEqualTo(7);
		assertThat(failures.getFirst().stepLabel()).isEqualTo("Claude — executive batch (C)");
		assertThat(failures.getFirst().errorMessage()).isEqualTo("Read timed out");
		assertThat(failures.getFirst().title()).isEqualTo("Q3 EOC deck");
		assertThat(failures.getFirst().ownerEmail()).isEqualTo("jane.doe@aidigital.com");
	}

	@Test
	void shouldSubstituteAPlaceholderWhenNoErrorMessageWasCapturedTest() {
		// Given: a failure whose exception carried no message — a bare NPE, typically.
		ReportJobEntity job = new ReportJobEntity();
		job.setStatus("error");
		job.setStep(3);
		job.setTotal(7);
		job.setErrorMessage(null);
		stubTitle("EOC report");

		// When:
		List<AdminFailedJob> failures = assembler().recentFailures(List.of(job), 10);

		// Then: the UI shows an explanation rather than an empty cell.
		assertThat(failures.getFirst().errorMessage()).isEqualTo("No error message was recorded.");
	}

	@Test
	void shouldKeepOnlyFailedJobsAndHonourTheLimitTest() {
		// Given: two failures among successful jobs, and room for one row.
		ReportJobEntity done = new ReportJobEntity();
		done.setStatus("done");
		ReportJobEntity firstFailure = new ReportJobEntity();
		firstFailure.setStatus("error");
		firstFailure.setStep(2);
		firstFailure.setTotal(7);
		ReportJobEntity secondFailure = new ReportJobEntity();
		secondFailure.setStatus("error");
		secondFailure.setStep(4);
		secondFailure.setTotal(7);
		stubTitle("EOC report");

		// When:
		List<AdminFailedJob> failures =
				assembler().recentFailures(List.of(done, firstFailure, secondFailure), 1);

		// Then: the newest failure only — the input list is already newest-first.
		assertThat(failures).hasSize(1);
		assertThat(failures.getFirst().step()).isEqualTo(2);
	}
}
