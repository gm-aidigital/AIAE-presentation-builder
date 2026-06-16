package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportJobProgressHelperImplTest {

	@Mock
	ReportJobRepository jobs;

	@InjectMocks
	ReportJobProgressHelperImpl helper;

	@Test
	void shouldCreateQueuedJobWithSevenStepsTest() {
		when(jobs.save(any(ReportJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		ReportJobEntity job = helper.createQueuedJob("user-1", "standard");

		ArgumentCaptor<ReportJobEntity> captor = ArgumentCaptor.forClass(ReportJobEntity.class);
		verify(jobs).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo("queued");
		assertThat(captor.getValue().getTotal()).isEqualTo(7);
		assertThat(captor.getValue().getOwnerUserId()).isEqualTo("user-1");
		assertThat(captor.getValue().getReportTypeCode()).isEqualTo("standard");
		assertThat(job.getLabel()).isEqualTo("Queued…");
	}

	@Test
	void shouldMarkJobRunningAtStepTest() {
		ReportJobEntity existing = new ReportJobEntity();
		existing.setId(5L);
		when(jobs.findById(5L)).thenReturn(Optional.of(existing));
		when(jobs.save(any(ReportJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		helper.markJobRunningAtStep(5L, 3, "Claude — campaign batch (A)");

		assertThat(existing.getStatus()).isEqualTo("running");
		assertThat(existing.getStep()).isEqualTo(3);
		assertThat(existing.getLabel()).isEqualTo("Claude — campaign batch (A)");
	}

	@Test
	void shouldMarkJobDoneTest() {
		ReportJobEntity existing = new ReportJobEntity();
		existing.setId(9L);
		when(jobs.findById(9L)).thenReturn(Optional.of(existing));
		when(jobs.save(any(ReportJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		helper.markJobDone(9L, "https://slides/d/abc123/edit", "[\"warn\"]");

		assertThat(existing.getStatus()).isEqualTo("done");
		assertThat(existing.getStep()).isEqualTo(7);
		assertThat(existing.getLabel()).isEqualTo("Done!");
		assertThat(existing.getSlideUrl()).isEqualTo("https://slides/d/abc123/edit");
		assertThat(existing.getWarningsJson()).isEqualTo("[\"warn\"]");
	}

	@Test
	void shouldThrowWhenJobNotOwnedByUserTest() {
		ReportJobEntity existing = new ReportJobEntity();
		existing.setOwnerUserId("other");
		when(jobs.findById(1L)).thenReturn(Optional.of(existing));

		Throwable thrown = catchThrowable(() -> helper.loadJobForOwner("user-1", 1L));

		assertThat(thrown)
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C001.getCode());
	}
}
