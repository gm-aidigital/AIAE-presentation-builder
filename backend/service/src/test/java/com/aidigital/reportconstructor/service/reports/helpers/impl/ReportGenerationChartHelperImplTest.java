package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.EffectiveTacticsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetChartDataReader;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartJob;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationChartHelperImplTest {

	@Mock
	ChartProvider charts;
	@Mock
	SlidesProvider slides;
	@Mock
	TacticExtractionHelper tacticExtraction;
	@Mock
	EffectiveTacticsHelper effectiveTactics;
	@Mock
	ReportNumberParser reportNumbers;
	@Mock
	SheetChartDataReader sheetChartData;
	@Mock
	BreakdownSelectionResolver breakdownResolver;
	@Mock
	ReportSheetHelper sheetHelper;

	@InjectMocks
	ReportGenerationChartHelperImpl helper;

	@Test
	void shouldExtractPresentationIdFromSlideUrlTest() {
		assertThat(helper.extractPresentationId("https://docs.google.com/presentation/d/abc-123_9/edit"))
				.isEqualTo("abc-123_9");
		assertThat(helper.extractPresentationId(null)).isNull();
	}

	@Test
	void shouldClampTheTacticCountAndSwallowFailuresWhenAddingTacticSlidesTest() {
		// Given: a provider that fails, and a tactic count above the template ceiling
		Map<String, String> values = Map.of("{{tactic 1}}", "Display");
		doThrow(new IllegalStateException("slides down"))
				.when(slides).addTacticSlides("pres-1", 28, values, "token");

		// When: the deck's tactic slides are built
		List<String> warnings =
				helper.addTacticSlides("https://docs.google.com/presentation/d/pres-1/edit", 99, values, "token");

		// Then: the count reached the provider clamped to 28, and the failure did not propagate — the deck
		// still ships, missing tactic slides rather than nothing at all — but it is reported as a job warning
		// carrying the provider's reason, not swallowed into the server log
		verify(slides).addTacticSlides("pres-1", 28, values, "token");
		assertThat(warnings).hasSize(1);
		assertThat(warnings.getFirst()).contains("28 tactic(s)").contains("slides down");
	}

	@Test
	void shouldReportNoWarningWhenTacticSlidesAreBuiltTest() {
		// Given-When: a provider that inserts the slides without failing
		List<String> warnings = helper.addTacticSlides(
				"https://docs.google.com/presentation/d/pres-1/edit", 2, Map.of(), "token");

		// Then: nothing is reported on the job
		assertThat(warnings).isEmpty();
	}

	@Test
	void shouldSkipAddingTacticSlidesWhenTheSlideUrlCarriesNoPresentationIdTest() {
		// Given-When: a slide url the presentation id cannot be parsed out of
		List<String> warnings = helper.addTacticSlides("not-a-slides-url", 3, Map.of(), "token");

		// Then: the provider is never called with a bogus deck id
		verify(slides, never()).addTacticSlides(any(), anyInt(), any(), any());
		assertThat(warnings).isEmpty();
	}

	@Test
	void shouldSkipBreakdownSlidesWhenSelectionsNullTest() {
		// Given: a payload with no breakdown selections
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), null, "", null, null, null);

		// When:
		helper.addBreakdownSlides("https://docs.google.com/presentation/d/pres-1/edit", payload, 5, Map.of(), "token");

		// Then: the slides provider is never asked to add breakdown slides
		verify(slides, never()).addBreakdownSlides(any(), any(), any(), any());
	}

	@Test
	void shouldInsertOnlyBreakdownSlidesWithinTacticCountTest() {
		// Given: the resolver reports tactic 1 (Top Publishers) and tactic 3 (Device) enabled, but only
		// 2 tactics are active
		List<BreakdownSelection> selections = List.of(
				new BreakdownSelection(1, List.of("tp")),
				new BreakdownSelection(3, List.of("dev")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, null, null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		resolved.put(3, EnumSet.of(BreakdownType.DEVICE));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);

		// When:
		helper.addBreakdownSlides("https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: only tactic 1 (within the active count) is passed to the provider; tactic 3 is dropped
		Map<Integer, Set<BreakdownType>> expected = new LinkedHashMap<>();
		expected.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		verify(slides).addBreakdownSlides(eq("pres-1"), eq(expected), eq(Map.of()), eq("token"));
	}

	@Test
	void shouldSkipBreakdownSlidesWhenAllSelectionsBeyondTacticCountTest() {
		// Given: the only enabled tactic is beyond the active count
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(5, List.of("tp")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, null, null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(5, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);

		// When:
		helper.addBreakdownSlides("https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: nothing is inserted
		verify(slides, never()).addBreakdownSlides(any(), any(), any(), any());
	}

	@Test
	void shouldBuildBreakdownChartJobsForAudienceAndDeviceTacticsTest() {
		// Given: tactic 1 enabled Device, tactic 2 enabled Audience (both within the active count), and each
		// sheet block carries one impressions row
		List<BreakdownSelection> selections = List.of(
				new BreakdownSelection(1, List.of("dev")),
				new BreakdownSelection(2, List.of("aud")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, "https://docs.google.com/spreadsheets/d/sheet-1/edit", null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(1, EnumSet.of(BreakdownType.DEVICE));
		resolved.put(2, EnumSet.of(BreakdownType.AUDIENCE));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);
		when(sheetHelper.readDeviceTables(eq("https://docs.google.com/spreadsheets/d/sheet-1/edit"), eq(Set.of(1)),
				eq("token"))).thenReturn(Map.of(1, new DeviceTable("", "", "", "", "",
				List.of(new DeviceRow("Mobile", "1,000", "", "", "")))));
		when(sheetHelper.readAudienceTables(eq("https://docs.google.com/spreadsheets/d/sheet-1/edit"), eq(Set.of(2)),
				eq("token"))).thenReturn(Map.of(2, new AudienceTable("", "",
				List.of(new AudienceAgeRow("25-34", "2,000")),
				List.of(new AudienceSegmentRow("In-Market: Luxury Travel", "142")))));
		when(reportNumbers.parseReportNumber("1,000")).thenReturn(1000.0);
		when(reportNumbers.parseReportNumber("2,000")).thenReturn(2000.0);
		when(reportNumbers.parseReportNumber("142")).thenReturn(142.0);

		// When:
		helper.buildBreakdownCharts(
				"https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: three jobs — the audience slide carries two charts (age + segments), the device slide one
		ArgumentCaptor<BreakdownChartRequest> captor = ArgumentCaptor.forClass(BreakdownChartRequest.class);
		verify(charts).buildBreakdownCharts(captor.capture());
		List<BreakdownChartJob> jobs = captor.getValue().jobs();
		assertThat(captor.getValue().presentationId()).isEqualTo("pres-1");
		assertThat(jobs).hasSize(3);
		assertThat(jobs).anySatisfy(job -> {
			assertThat(job.seriesCode()).isEqualTo("aud");
			assertThat(job.tacticNum()).isEqualTo(2);
			assertThat(job.slices()).singleElement().satisfies(slice -> {
				assertThat(slice.label()).isEqualTo("25-34");
				assertThat(slice.value()).isEqualTo(2000.0);
			});
		});
		// And: the segment chart plots the affinity index, the only number the sheet's segment table carries
		assertThat(jobs).anySatisfy(job -> {
			assertThat(job.seriesCode()).isEqualTo("aud-seg");
			assertThat(job.tacticNum()).isEqualTo(2);
			assertThat(job.slices()).singleElement().satisfies(slice -> {
				assertThat(slice.label()).isEqualTo("In-Market: Luxury Travel");
				assertThat(slice.value()).isEqualTo(142.0);
			});
		});
		assertThat(jobs).anySatisfy(job -> {
			assertThat(job.seriesCode()).isEqualTo("dev");
			assertThat(job.tacticNum()).isEqualTo(1);
			assertThat(job.slices()).singleElement().satisfies(slice -> {
				assertThat(slice.label()).isEqualTo("Mobile");
				assertThat(slice.value()).isEqualTo(1000.0);
			});
		});
	}

	@Test
	void shouldSkipBreakdownChartsWhenNoAudienceOrDeviceEnabledTest() {
		// Given: only Top Publishers is enabled — a section with no chart
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("tp")));
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(), selections, "", null, "https://docs.google.com/spreadsheets/d/sheet-1/edit", null);
		Map<Integer, Set<BreakdownType>> resolved = new LinkedHashMap<>();
		resolved.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		when(breakdownResolver.resolve(selections)).thenReturn(resolved);

		// When:
		List<String> warnings = helper.buildBreakdownCharts(
				"https://docs.google.com/presentation/d/pres-1/edit", payload, 2, Map.of(), "token");

		// Then: no chart request is built and the sheet is never read for chart data
		assertThat(warnings).isEmpty();
		verify(charts, never()).buildBreakdownCharts(any());
		verify(sheetHelper, never()).readDeviceTables(any(), any(), any());
		verify(sheetHelper, never()).readAudienceTables(any(), any(), any());
	}

	@Test
	void shouldSkipChartsWhenRequiredInputsMissingTest() {
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);

		List<String> warnings = helper.buildCharts(
				"https://docs.google.com/presentation/d/abc/edit",
				payload,
				emptyCampaignData(),
				Map.of(),
				"token"
		);

		assertThat(warnings).isEmpty();
		verify(charts, never()).buildCharts(any());
	}

	@Test
	void shouldReturnSkipWarningWhenPresentationIdMissingTest() {
		GeneratePayload payload = payloadWithChartInputs();

		List<String> warnings = helper.buildCharts(
				"https://example.com/no-id",
				payload,
				emptyCampaignData(),
				placeholderMap(),
				"token"
		);

		assertThat(warnings).containsExactly(
				"Charts skipped — could not determine presentation id from https://example.com/no-id");
	}

	@Test
	void shouldTrimTacticsWhenPresentationIdPresentTest() {
		GeneratePayload payload = payloadWithChartInputs();
		when(effectiveTactics.effectiveTacticCount(payload.sheetRows(), payload.lineItemMapping())).thenReturn(3);

		helper.trimUnusedTactics("https://docs.google.com/presentation/d/deck-id/edit", payload, "token");

		verify(slides).trimTactics(eq("deck-id"), eq(3), eq("token"));
	}

	@Test
	void shouldTrimTacticsForExplicitCountWhenPresentationIdPresentTest() {
		// Given: an explicit tactic count (the "Slides from Sheet" flow has no Media Plan)

		// When: the deck is trimmed by explicit count
		helper.trimUnusedTactics("https://docs.google.com/presentation/d/deck-id/edit", 2, "token");

		// Then: the count is passed through, without consulting the Media Plan extractor
		verify(slides).trimTactics(eq("deck-id"), eq(2), eq("token"));
		verifyNoInteractions(tacticExtraction);
	}

	@Test
	void shouldBuildChartsFromSheetPivotsWithoutBigQueryTest() {
		// Given: a sheet grid and placeholders for one CTR tactic, with a reconstructed daily pivot
		List<List<String>> grid = List.of(List.of("{{tactic 1 date}}"));
		Map<String, String> flat = Map.of(
				"{{Campaign_name}}", "Spring",
				"{{tactic 1}}", "Display",
				"{{tactic 1 imps}}", "1,000",
				"{{total imps}}", "1,000");
		when(tacticExtraction.getTacticKpiSeries("Display")).thenReturn("ctr");
		when(reportNumbers.parseReportNumber("1,000")).thenReturn(1000.0);
		LinkedHashMap<String, double[]> series = new LinkedHashMap<>();
		series.put("Jun 1", new double[] {1000.0, 10.0, 0.0});
		SheetChartData chartData = new SheetChartData(
				Map.of(1, new Pivot(series, true, false)),
				Map.of(1, new Pivot(new LinkedHashMap<>(), false, false)));
		when(sheetChartData.read(grid, 1, Map.of(1, "ctr"))).thenReturn(chartData);
		when(charts.buildCharts(any())).thenReturn(List.of());

		// When: charts are built from the sheet
		List<String> warnings = helper.buildChartsFromSheet(
				"https://docs.google.com/presentation/d/deck-id/edit", grid, flat, 1, "token");

		// Then: the request carries the sheet pivots and no BigQuery rows
		assertThat(warnings).isEmpty();
		ArgumentCaptor<ChartRequest> captor = ArgumentCaptor.forClass(ChartRequest.class);
		verify(charts).buildCharts(captor.capture());
		ChartRequest req = captor.getValue();
		assertThat(req.presentationId()).isEqualTo("deck-id");
		assertThat(req.bqRows()).isEmpty();
		assertThat(req.lineItemMapping()).isEmpty();
		assertThat(req.dailyPivots()).containsKey(1);
		assertThat(req.monthlyPivots()).containsKey(1);
		assertThat(req.tacticKpiTypes()).containsEntry(1, "ctr");
		assertThat(req.distTacticNames()).containsEntry(1, "Display");
	}

	@Test
	void shouldReturnSkipWarningWhenPresentationIdMissingForSheetChartsTest() {
		// Given: a slide URL with no parseable presentation id

		// When: sheet charts are requested
		List<String> warnings = helper.buildChartsFromSheet(
				"https://example.com/no-id", List.of(), Map.of(), 1, "token");

		// Then: a skip warning is returned and no chart provider call is made
		assertThat(warnings).containsExactly(
				"Charts skipped — could not determine presentation id from https://example.com/no-id");
		verify(charts, never()).buildCharts(any());
	}

	private static GeneratePayload payloadWithChartInputs() {
		return new GeneratePayload(
				"brief",
				"standard",
				"",
				List.of(List.of("Media"), List.of("Display")),
				List.of(List.of("Label", "Value")),
				List.of(),
				List.of(),
				List.of(),
				List.of(new LineItemMapping("Display", "99", 1)),
				null,
				"sheet-id",
				null,
				null,
				null
		);
	}

	private static CampaignData emptyCampaignData() {
		return new CampaignData(
				"", "", "", "", "",
				new FlightDates(null, null),
				"", "", "", "", "",
				new Totals(0, 0, 0, 0, null, null),
				Map.of(),
				""
		);
	}

	private static Map<String, String> placeholderMap() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("{{Campaign_name}}", "Spring Campaign");
		map.put("{{total imps}}", "1,234");
		map.put("{{tactic 1}}", "Display");
		map.put("{{tactic 1 imps}}", "500");
		return map;
	}
}
