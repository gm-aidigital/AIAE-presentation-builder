package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceImplTest {

	@Mock
	ReportJobProgressHelper jobProgress;
	@Mock
	ReportGenerationWarningsHelper warnings;
	@Mock
	ReportGenerationChartHelper chartHelper;
	@Mock
	PlaceholderResolverService placeholders;
	@Mock
	ClaudeClient claude;
	@Mock
	SlidesProvider slides;
	@Mock
	ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
	@Mock
	ObjectProvider<ReportGenerationService> self;
	@Mock
	ReportClaudeDefaults claudeDefaults;

	ReportGenerationServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new ReportGenerationServiceImpl(
				jobProgress, warnings, chartHelper, placeholders, claude, slides,
				userGoogleTokens, self, claudeDefaults);
	}

	@Test
	void shouldThrowAppExceptionWhenBriefIsBlankTest() {
		GeneratePayload payload = new GeneratePayload(
				"  ", "standard", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");

		Throwable thrown = catchThrowable(() -> service.start("user-1", "clerk-1", payload));

		assertThat(thrown)
				.isInstanceOf(AppException.class)
				.hasFieldOrPropertyWithValue("code", ErrorReason.C002.getCode());
	}

	@Test
	void shouldDelegateEnqueueToJobProgressHelperTest() {
		ReportJobEntity queued = new ReportJobEntity();
		queued.setId(99L);
		queued.setStatus("queued");
		queued.setTotal(7);
		queued.setOwnerUserId("user-1");
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
		when(jobProgress.createQueuedJob("user-1", "standard")).thenReturn(queued);

		ReportJobEntity job = service.enqueue("user-1", payload);

		verify(jobProgress).createQueuedJob("user-1", "standard");
		assertThat(job.getId()).isEqualTo(99L);
		assertThat(job.getStatus()).isEqualTo("queued");
		assertThat(job.getTotal()).isEqualTo(7);
	}

	@Test
	void shouldEnqueueAndKickOffAsyncRunOnStartTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
		ReportJobEntity queued = new ReportJobEntity();
		queued.setId(5L);
		when(jobProgress.createQueuedJob("user-1", "standard")).thenReturn(queued);
		ReportGenerationService selfBean = mock(ReportGenerationService.class);
		when(self.getObject()).thenReturn(selfBean);

		ReportJobEntity job = service.start("user-1", "clerk-1", payload);

		assertThat(job).isSameAs(queued);
		verify(selfBean).run(5L, payload, "clerk-1");
	}

	@Test
	void shouldRunPipelineAndMarkJobDoneWhenClaudeOfflineTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
		when(claude.isLive()).thenReturn(false);
		when(placeholders.buildFlatReplacements(any(), any(), any(), any(), any(), any())).thenReturn(Map.of());
		when(slides.createDeck(eq("7"), any(), isNull())).thenReturn("http://deck");
		when(chartHelper.buildCharts(eq("http://deck"), any(), any(), any(), isNull())).thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		service.run(7L, payload, "clerk-1");

		verify(chartHelper).trimUnusedTactics("http://deck", payload, null);
		verify(jobProgress).markJobDone(7L, "http://deck", "[]");
	}

	@Test
	void shouldMarkJobFailedWhenPipelineThrowsTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
		when(placeholders.collectData(payload)).thenThrow(new RuntimeException("boom"));

		service.run(8L, payload, "clerk-1");

		verify(jobProgress).markJobFailed(8L, "boom");
	}

	@Test
	void shouldReturnNullSafeProgressViewTest() {
		ReportJobEntity job = new ReportJobEntity();
		job.setStep(3);
		job.setTotal(7);
		job.setLabel("Building slide deck");
		job.setStatus("running");
		job.setSlideUrl(null);
		job.setErrorMessage(null);
		job.setWarningsJson("[]");
		when(jobProgress.loadJobForOwner("user-1", 9L)).thenReturn(job);
		when(warnings.parseWarnings("[]")).thenReturn(List.of("w1"));

		ProgressView view = service.progress("user-1", 9L);

		assertThat(view.step()).isEqualTo(3);
		assertThat(view.total()).isEqualTo(7);
		assertThat(view.label()).isEqualTo("Building slide deck");
		assertThat(view.status()).isEqualTo("running");
		assertThat(view.slideUrl()).isEmpty();
		assertThat(view.error()).isEmpty();
		assertThat(view.warnings()).containsExactly("w1");
	}
}
