package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeSheetBatch;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.helpers.CreativeBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.AudienceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.DeviceBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.GeoBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.PublisherBreakdownHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportFileNamer;
import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportNumberParserImpl;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetCampaignReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.ReportGenerationService;
import com.aidigital.reportconstructor.service.reports.usage.impl.ClaudeUsageTrackerImpl;
import com.aidigital.reportconstructor.service.reports.usage.impl.NoOpClaudeUsageEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
	ReportSheetHelper sheetHelper;
	@Mock
	SheetPlaceholderReader placeholderReader;
	@Mock
	SheetCampaignReader sheetCampaign;
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
	@Mock
	ReportFileNamer fileNamer;
	@Mock
	PublisherBreakdownHelper publisherBreakdown;
	@Mock
	CreativeBreakdownHelper creativeBreakdown;
	@Mock
	GeoBreakdownHelper geoBreakdown;
	@Mock
	AudienceBreakdownHelper audienceBreakdown;
	@Mock
	DeviceBreakdownHelper deviceBreakdown;

	ReportGenerationServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new ReportGenerationServiceImpl(
				jobProgress, warnings, chartHelper, sheetHelper, publisherBreakdown, creativeBreakdown, geoBreakdown, audienceBreakdown, deviceBreakdown, placeholderReader, sheetCampaign, placeholders,
				claude, slides, userGoogleTokens, self, claudeDefaults, fileNamer,
				new ReportNumberParserImpl(), new Fmt(), new SimpleAsyncTaskExecutor(),
				new ClaudeUsageTrackerImpl(new NoOpClaudeUsageEventService()));
	}

	@Test
	void shouldThrowAppExceptionWhenBriefIsBlankTest() {
		GeneratePayload payload = new GeneratePayload(
				"  ", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);

		Throwable thrown = catchThrowable(
				() -> service.start("user-1", "clerk-1", "user@x.com", payload, GenerationTarget.SLIDES, null, null));

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
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
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
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		ReportJobEntity queued = new ReportJobEntity();
		queued.setId(5L);
		when(jobProgress.createQueuedJob("user-1", "standard")).thenReturn(queued);
		ReportGenerationService selfBean = mock(ReportGenerationService.class);
		when(self.getObject()).thenReturn(selfBean);

		ReportJobEntity job = service.start(
				"user-1", "clerk-1", "user@x.com", payload, GenerationTarget.SLIDES, "http://plan", "http://elevate");

		assertThat(job).isSameAs(queued);
		verify(selfBean).run(5L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES);
		verify(jobProgress).recordJobContext(5L, "user@x.com", "SLIDES", "http://plan", "http://elevate");
	}

	@Test
	void shouldRunPipelineAndMarkJobDoneWhenClaudeOfflineTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		when(claude.isLive()).thenReturn(false);
		when(placeholders.buildFlatReplacements(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
				.thenReturn(Map.of());
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("deck-file");
		when(slides.createDeck(eq("7"), eq("deck-file"), any(), isNull())).thenReturn("http://deck");
		when(chartHelper.buildCharts(eq("http://deck"), any(), any(), any(), isNull())).thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		service.run(7L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES);

		verify(chartHelper).trimUnusedTactics("http://deck", payload, null);
		verify(jobProgress).markJobDone(7L, "http://deck", "[]");
		// The deck placeholder map is bounded to the real tactic count (here the collector yields none, so 1),
		// never the full 28 template slots — this is what keeps createDeck's find-replace from timing out.
		verify(placeholders).buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1));
	}

	@Test
	void shouldBuildSheetAndSkipChartsWhenTargetIsSheetTest() {
		// Given:
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		when(claude.isLive()).thenReturn(false);
		when(claudeDefaults.emptySheetBatch()).thenReturn(new ClaudeSheetBatch(null, null, Map.of()));
		when(placeholders.buildFlatReplacements(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
				.thenReturn(Map.of());
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("sheet-file");
		when(sheetHelper.buildSheet("9", "sheet-file", Map.of(), null)).thenReturn("http://sheet");
		when(sheetHelper.writePacingTables(eq("http://sheet"), eq(payload), any(), eq(Map.of()), isNull()))
				.thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		// When:
		service.run(9L, payload, "clerk-1", "user@x.com", GenerationTarget.SHEET);

		// Then:
		verify(sheetHelper).trimUnusedTactics("http://sheet", payload, null);
		verify(jobProgress).markJobDone(9L, "http://sheet", "[]");
		// The Sheet placeholder map is bounded to the real tactic count too (here the collector yields none,
		// so 1), not all 28 slots — the ~800-request find-replace was the createSheet "Read timed out" cause.
		// Unused slots' leftover tokens are blanked by a single regex pass in createSheet.
		verify(placeholders).buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(1));
		verifyNoInteractions(slides);
		verifyNoInteractions(chartHelper);
	}

	@Test
	void shouldFillDeckFromSheetWithNarrativeOverlaidBySheetValuesTest() {
		// Given: a step-2 request naming the user-edited sheet, whose grid yields two filled tactics
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, "http://sheet", null);
		List<List<String>> grid = List.of(List.of("Tactic name"), List.of("CTV"), List.of("Display"));
		Map<String, String> sheetValues = Map.of(
				"{{client_name}}", "Acme", "{{tactic 1}}", "CTV", "{{tactic 2}}", "Display");
		// Narrative carries a sheet-less recommendation and a client name the sheet must override
		Map<String, String> narrative = Map.of("{{client_name}}", "From Claude", "{{recommendation 1}}", "Do X");
		when(sheetHelper.readSheetGrid("http://sheet", null)).thenReturn(grid);
		when(placeholderReader.readPlaceholders(grid)).thenReturn(sheetValues);
		when(claude.isLive()).thenReturn(false);
		when(placeholders.buildFlatReplacements(
				any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(narrative);
		when(fileNamer.buildFileName(any(), any(), any())).thenReturn("deck-file");
		when(slides.createDeck(eq("11"), eq("deck-file"), any(), isNull())).thenReturn("http://deck");
		when(publisherBreakdown.buildPublisherValues(
				eq("http://sheet"), isNull(), any(), eq("Campaign brief."), isNull()))
				.thenReturn(BreakdownValues.EMPTY);
		when(creativeBreakdown.buildCreativeValues(
				eq("http://sheet"), isNull(), any(), eq("Campaign brief."), isNull()))
				.thenReturn(BreakdownValues.EMPTY);
		when(geoBreakdown.buildGeoValues(
				eq("http://sheet"), isNull(), any(), eq("Campaign brief."), isNull()))
				.thenReturn(BreakdownValues.EMPTY);
		when(audienceBreakdown.buildAudienceValues(
				eq("http://sheet"), isNull(), any(), eq("Campaign brief."), isNull()))
				.thenReturn(BreakdownValues.EMPTY);
		when(deviceBreakdown.buildDeviceValues(
				eq("http://sheet"), isNull(), any(), eq("Campaign brief."), isNull()))
				.thenReturn(BreakdownValues.EMPTY);
		when(chartHelper.buildChartsFromSheet(eq("http://deck"), eq(grid), any(), eq(2), isNull()))
				.thenReturn(List.of());
		when(warnings.serializeWarnings(List.of())).thenReturn("[]");

		// When: the slides-from-sheet job runs
		service.run(11L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES_FROM_SHEET);

		// Then: it reconstructs campaign context from the sheet, then merges narrative UNDER the sheet values
		verify(sheetCampaign).read(sheetValues, 2);
		ArgumentCaptor<Map<String, String>> deckMap = ArgumentCaptor.forClass(Map.class);
		verify(slides).createDeck(eq("11"), eq("deck-file"), deckMap.capture(), isNull());
		assertThat(deckMap.getValue())
				.containsEntry("{{client_name}}", "Acme")
				.containsEntry("{{recommendation 1}}", "Do X");
		verify(chartHelper).trimUnusedTactics("http://deck", 2, null);
		verify(chartHelper).buildChartsFromSheet(eq("http://deck"), eq(grid), any(), eq(2), isNull());
		verify(jobProgress).markJobDone(11L, "http://deck", "[]");
		// And: the raw-grid collection and the offline Claude batches never run — no duplicate work
		verify(placeholders, never()).collectData(any());
		verify(claude, never()).batchStrategicNarrative(any(), any());
		verify(claude, never()).batchResults(any(), any(), any());
	}

	@Test
	void shouldMarkJobFailedWhenPipelineThrowsTest() {
		GeneratePayload payload = new GeneratePayload(
				"Campaign brief.", "standard", "1000000", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
		when(placeholders.collectData(payload)).thenThrow(new RuntimeException("boom"));

		service.run(8L, payload, "clerk-1", "user@x.com", GenerationTarget.SLIDES);

		verify(jobProgress).markJobFailed(8L, "boom");
	}

	@Test
	void shouldAliasReachFactFromReachColumnAndCompactShortTokensTest() {
		// Given: the sheet reader loaded reach_f with the Frequency total (its internal channel for the
		// frequency narrative) and the full campaign reach sits in {{reach}}
		Map<String, String> flat = new HashMap<>();
		flat.put("{{reach}}", "70,001");
		flat.put("{{reach_f}}", "5.383523093");

		// When: the sheet tokens are aliased for the deck
		service.aliasSheetTokens(flat, 0);

		// Then: "Market Captured" ({{reach_f}}) is reclaimed from the Reach column, not the Frequency total,
		// and the presentation-short reach tokens render it abbreviated
		assertThat(flat)
				.containsEntry("{{reach_f}}", "70,001")
				.containsEntry("{{reach_p}}", "70k")
				.containsEntry("{{reach_f_pres}}", "70k");
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

	@Test
	void shouldFillFunnelStagesFromTheReviewedTacticGoalsTest() {
		// Given: a map whose funnel token is an unresolved dash and whose two tactics carry goals
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", "—");
		flat.put("{{tactic 1 goal}}", "Build awareness");
		flat.put("{{tactic 2 goal}}", "Drive site visits");
		when(claude.summarizeFunnelStages(List.of("Build awareness", "Drive site visits")))
				.thenReturn("Awareness, Consideration");

		// When:
		service.fillFunnelStages(flat, 2, true);

		// Then: the goals — never the source workbook — produced the funnel line
		assertThat(flat).containsEntry("{{funnel_stages}}", "Awareness, Consideration");
		verify(claude).summarizeFunnelStages(List.of("Build awareness", "Drive site visits"));
	}

	@Test
	void shouldKeepAnAlreadyResolvedFunnelValueWithoutCallingClaudeTest() {
		// Given: a funnel value the media plan or the user already supplied
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", "Awareness, Conversion");
		flat.put("{{tactic 1 goal}}", "Build awareness");

		// When:
		service.fillFunnelStages(flat, 1, true);

		// Then: a reviewed value is never overwritten and costs no request
		assertThat(flat).containsEntry("{{funnel_stages}}", "Awareness, Conversion");
		verifyNoInteractions(claude);
	}

	@Test
	void shouldLeaveFunnelStagesUntouchedWhenNoTacticCarriesAGoalTest() {
		// Given: dash-filled goals, which is what unused tactic slots render as
		Map<String, String> flat = new HashMap<>();
		flat.put("{{funnel_stages}}", "—");
		flat.put("{{tactic 1 goal}}", "—");

		// When:
		service.fillFunnelStages(flat, 1, true);

		// Then:
		assertThat(flat).containsEntry("{{funnel_stages}}", "—");
		verifyNoInteractions(claude);
	}
}
