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
	void shouldBuildThirtyFourSectionsWithExpectedTitlesTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 28
		);

		// 4 lead sections + 28 per-tactic sections + Optimization Recommendations + Frequency Story = 34
		assertThat(sections).hasSize(34);
		assertThat(sections.get(0).title()).isEqualTo("Start");
		assertThat(sections.get(1).title()).isEqualTo("Overview Slides");
		assertThat(sections.get(2).title()).isEqualTo("Strategic Insights");
		assertThat(sections.get(3).title()).isEqualTo("Summary Metrics");
		assertThat(sections.get(4).title()).isEqualTo("Tactic 1");
		assertThat(sections.get(31).title()).isEqualTo("Tactic 28");
		assertThat(sections.get(32).title()).isEqualTo("Optimization Recommendations");
		assertThat(sections.get(32).placeholders())
				.extracting(Placeholder::key)
				.containsExactly(
						"{{recommendation 1}}", "{{recommendation 1 text}}",
						"{{recommendation 2}}", "{{recommendation 2 text}}",
						"{{recommendation 3}}", "{{recommendation 3 text}}",
						"{{recommendation 4}}", "{{recommendation 4 text}}");
		assertThat(sections.get(33).title()).isEqualTo("Frequency Story");
		assertThat(sections.get(33).placeholders())
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
				null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 28
		);

		assertThat(sections.get(4).placeholders()).hasSize(26);
		assertThat(sections.get(4).placeholders())
				.extracting(Placeholder::key)
				.contains("{{tactic 1 volume}}", "{{tactic 1 top creative name}}");
	}

	@Test
	void shouldUseFullPlaceholderSetForTacticSevenTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 28
		);

		assertThat(sections.get(10).placeholders()).hasSize(26);
		assertThat(sections.get(10).placeholders())
				.extracting(Placeholder::key)
				.contains("{{tactic 7 volume}}", "{{tactic 7 top creative name}}",
						"{{tactic 7 top creative imps}}", "{{tactic 7 top creative clicks}}");
	}

	@Test
	void shouldBuildOnlyRequestedNumberOfTacticSectionsTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 2
		);

		// 4 lead sections + 2 per-tactic sections + Optimization Recommendations + Frequency Story = 8
		assertThat(sections).hasSize(8);
		assertThat(sections.get(4).title()).isEqualTo("Tactic 1");
		assertThat(sections.get(5).title()).isEqualTo("Tactic 2");
		assertThat(sections.get(6).title()).isEqualTo("Optimization Recommendations");
		assertThat(sections).noneMatch(s -> s.title().equals("Tactic 3"));
	}

	private static GeneratePayload minimalPayload() {
		return new GeneratePayload(
				"brief",
				"standard",
				"",
				List.of(
						List.of("Media", "Comments"),
						List.of("Programmatic Display", "")
				),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				null,
				"",
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
}
