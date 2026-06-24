package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.engine.CampaignDataCollector;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderClaudeGate;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderLabelCollector;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderSectionBuilder;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderValueFlattener;
import com.aidigital.reportconstructor.service.reports.services.Labels;
import com.aidigital.reportconstructor.service.reports.services.PreviewResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceholderResolverServiceImplTest {

	@Mock
	CampaignDataCollector campaignDataCollector;
	@Mock
	PlaceholderSectionBuilder sectionBuilder;
	@Mock
	PlaceholderClaudeGate claudeGate;
	@Mock
	PlaceholderLabelCollector labelCollector;
	@Mock
	PlaceholderValueFlattener valueFlattener;
	@Mock
	ReportClaudeDefaults claudeDefaults;

	@InjectMocks
	PlaceholderResolverServiceImpl service;

	private static GeneratePayload payload(List<List<String>> sheetRows, List<List<String>> adjRows) {
		return new GeneratePayload(
				"brief", "standard", sheetRows, adjRows,
				List.of(), List.of(), List.of(), List.of(), "");
	}

	private static CampaignData emptyData() {
		return new CampaignData(
				null, null, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	@Test
	void shouldCountResolvedAndTotalPlaceholdersForPreviewTest() {
		GeneratePayload payload = payload(
				List.of(List.of("r1"), List.of("r2")),
				List.of(List.of("a1")));
		PreviewSection section = new PreviewSection("Start", List.of(
				new Placeholder("{{a}}", "A", "v1", "sheet"),
				new Placeholder("{{b}}", "B", "v2", "claude"),
				new Placeholder("{{c}}", "C", null, "not_found")));
		when(sectionBuilder.buildSections(any(), any(), any(), any(), any(), any()))
				.thenReturn(List.of(section));
		when(labelCollector.collectAllLabels(payload))
				.thenReturn(new Labels(List.of(), List.of()));

		PreviewResult result = service.resolve(payload);

		assertThat(result.total()).isEqualTo(3);
		assertThat(result.found()).isEqualTo(2);
		assertThat(result.sheetCount()).isEqualTo(2);
		assertThat(result.adjCount()).isEqualTo(1);
		assertThat(result.sections()).hasSize(1);
	}

	@Test
	void shouldFlattenBuiltSectionsForReplacementsTest() {
		GeneratePayload payload = payload(List.of(), List.of());
		List<PreviewSection> sections = List.of(new PreviewSection("S", List.of()));
		Map<String, String> flat = Map.of("{{x}}", "1");
		when(sectionBuilder.buildSections(any(), any(), any(), any(), any(), any())).thenReturn(sections);
		when(valueFlattener.buildFlatReplacements(sections)).thenReturn(flat);

		Map<String, String> result = service.buildFlatReplacements(payload, null, null, null, null, null);

		assertThat(result).isEqualTo(flat);
	}

	@Test
	void shouldDelegateCollectDataToCollectorTest() {
		GeneratePayload payload = payload(List.of(List.of("r")), List.of());
		CampaignData data = emptyData();
		when(campaignDataCollector.collect(any(), any(), any(), any(), any())).thenReturn(data);

		CampaignData result = service.collectData(payload);

		assertThat(result).isSameAs(data);
		verify(campaignDataCollector).collect(
				payload.sheetRows(), payload.adjRows(), payload.audienceRows(),
				payload.estimatesRows(), payload.lineItemMapping());
	}

	@Test
	void shouldDelegateClaudeGatesToClaudeGateTest() {
		GeneratePayload payload = payload(List.of(), List.of());
		CampaignData data = emptyData();
		when(claudeGate.needStrategic(payload)).thenReturn(true);
		when(claudeGate.needTactical(payload, data)).thenReturn(true);
		when(claudeGate.needResults(payload, data)).thenReturn(false);
		when(claudeGate.needGeoSummary(payload)).thenReturn(true);

		assertThat(service.needStrategic(payload)).isTrue();
		assertThat(service.needTactical(payload, data)).isTrue();
		assertThat(service.needResults(payload, data)).isFalse();
		assertThat(service.needGeoSummary(payload)).isTrue();
	}
}
