package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.engine.ReportsEngineTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderSectionBuilderImplTest {

	private final PlaceholderSectionBuilderImpl builder = ReportsEngineTestSupport.placeholderSectionBuilder();
	private final ReportClaudeDefaults claudeDefaults = new ReportClaudeDefaults();

	@Test
	void shouldBuildThirteenSectionsWithExpectedTitlesTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null,
				new CampaignFrequencies(null, null, null, null)
		);

		assertThat(sections).hasSize(13);
		assertThat(sections.get(0).title()).isEqualTo("Start");
		assertThat(sections.get(1).title()).isEqualTo("Overview Slides");
		assertThat(sections.get(2).title()).isEqualTo("Strategic Insights");
		assertThat(sections.get(3).title()).isEqualTo("Summary Metrics");
		assertThat(sections.get(4).title()).isEqualTo("Tactic 1");
		assertThat(sections.get(10).title()).isEqualTo("Tactic 7");
		assertThat(sections.get(11).title()).isEqualTo("Optimization Recommendations");
		assertThat(sections.get(11).placeholders())
				.extracting(Placeholder::key)
				.containsExactly(
						"{{recommendation 1}}", "{{recommendation 1 text}}",
						"{{recommendation 2}}", "{{recommendation 2 text}}",
						"{{recommendation 3}}", "{{recommendation 3 text}}",
						"{{recommendation 4}}", "{{recommendation 4 text}}");
		assertThat(sections.get(12).title()).isEqualTo("Frequency Story");
		assertThat(sections.get(12).placeholders())
				.extracting(Placeholder::key)
				.containsExactly("{{f_oppartunity}}", "{{f_fact}}", "{{f_storytelling}}");
	}

	@Test
	void shouldUseFullPlaceholderSetForTacticsOneThroughSixTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null,
				new CampaignFrequencies(null, null, null, null)
		);

		assertThat(sections.get(4).placeholders()).hasSize(18);
		assertThat(sections.get(4).placeholders())
				.extracting(Placeholder::key)
				.contains("{{tactic 1 volume}}", "{{tactic 1 top creative name}}");
	}

	@Test
	void shouldUseShortPlaceholderSetForTacticSevenTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null,
				new CampaignFrequencies(null, null, null, null)
		);

		assertThat(sections.get(10).placeholders()).hasSize(14);
		assertThat(sections.get(10).placeholders())
				.extracting(Placeholder::key)
				.doesNotContain("{{tactic 7 volume}}", "{{tactic 7 top creative name}}");
	}

	private static GeneratePayload minimalPayload() {
		return new GeneratePayload(
				"brief",
				"standard",
				List.of(
						List.of("Media", "Comments"),
						List.of("Programmatic Display", "")
				),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				""
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
}
