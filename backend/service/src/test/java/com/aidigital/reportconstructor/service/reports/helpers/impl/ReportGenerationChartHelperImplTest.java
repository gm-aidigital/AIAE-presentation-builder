package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetChartDataReader;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
	ReportNumberParser reportNumbers;
	@Mock
	SheetChartDataReader sheetChartData;

	@InjectMocks
	ReportGenerationChartHelperImpl helper;

	@Test
	void shouldExtractPresentationIdFromSlideUrlTest() {
		assertThat(helper.extractPresentationId("https://docs.google.com/presentation/d/abc-123_9/edit"))
				.isEqualTo("abc-123_9");
		assertThat(helper.extractPresentationId(null)).isNull();
	}

	@Test
	void shouldSkipChartsWhenRequiredInputsMissingTest() {
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", null, null, null);

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
		when(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows())).thenReturn(3);

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
