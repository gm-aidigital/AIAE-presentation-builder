package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.dto.TacticInsight;
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
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 28
		);

		// 4 lead sections + 28 per-tactic sections + Optimization Recommendations + Frequency Story = 34
		assertThat(sections).hasSize(35);
		assertThat(sections.get(0).title()).isEqualTo("Start");
		assertThat(sections.get(1).title()).isEqualTo("Overview Slides");
		assertThat(sections.get(2).title()).isEqualTo("Strategic Insights");
		assertThat(sections.get(3).title()).isEqualTo("Summary Metrics");
		assertThat(sections.get(4).title()).isEqualTo("Tactic 1");
		assertThat(sections.get(31).title()).isEqualTo("Tactic 28");
		assertThat(sections.get(32).title()).isEqualTo("Funnel Channels");
		assertThat(sections.get(33).title()).isEqualTo("Optimization Recommendations");
		assertThat(sections.get(33).placeholders())
				.extracting(Placeholder::key)
				.containsExactly(
						"{{recommendation 1}}", "{{recommendation 1 text}}",
						"{{recommendation 2}}", "{{recommendation 2 text}}",
						"{{recommendation 3}}", "{{recommendation 3 text}}",
						"{{recommendation 4}}", "{{recommendation 4 text}}");
		assertThat(sections.get(34).title()).isEqualTo("Frequency Story");
		assertThat(sections.get(34).placeholders())
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
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 28
		);

		assertThat(sections.get(4).placeholders()).hasSize(32);
		assertThat(sections.get(4).placeholders())
				.extracting(Placeholder::key)
				.contains("{{tactic 1 volume}}", "{{tactic 1 top creative name}}", "{{so what 1}}");
	}

	@Test
	void shouldUseFullPlaceholderSetForTacticSevenTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 28
		);

		assertThat(sections.get(10).placeholders()).hasSize(32);
		assertThat(sections.get(10).placeholders())
				.extracting(Placeholder::key)
				.contains("{{tactic 7 volume}}", "{{tactic 7 top creative name}}",
						"{{tactic 7 top creative imps}}", "{{tactic 7 top creative clicks}}", "{{so what 7}}");
	}

	@Test
	void shouldBuildOnlyRequestedNumberOfTacticSectionsTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 2
		);

		// 4 lead sections + 2 per-tactic sections + Optimization Recommendations + Frequency Story = 8
		assertThat(sections).hasSize(9);
		assertThat(sections.get(4).title()).isEqualTo("Tactic 1");
		assertThat(sections.get(5).title()).isEqualTo("Tactic 2");
		assertThat(sections.get(6).title()).isEqualTo("Funnel Channels");
		assertThat(sections.get(7).title()).isEqualTo("Optimization Recommendations");
		assertThat(sections).noneMatch(s -> s.title().equals("Tactic 3"));
	}

	@Test
	void shouldEstimateDaypartGenderFromClaudeWhenOnTest() {
		// Given: Batch B supplies per-tactic gender/daypart copy and the estimate is on (default)
		GeneratePayload payload = minimalPayload();
		ClaudeTactical ccB = new ClaudeTactical(Map.of(1, new TacticInsight(60, 40, "Mon-Fri peak", "Sat-Sun peak")));

		// When: the tactic 1 section is built
		List<PreviewSection> sections = builder.buildSections(
				payload, emptyCampaignData(),
				claudeDefaults.emptyStrategic(), ccB, claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 1
		);

		// Then: the four dayparting/gender tokens carry the Claude estimate
		Map<String, String> tactic1 = tacticValues(sections.get(4));
		assertThat(tactic1.get("{{tactic 1 male}}")).isEqualTo("60%");
		assertThat(tactic1.get("{{tactic 1 female}}")).isEqualTo("40%");
		assertThat(tactic1.get("{{tactic 1 weekdays}}")).isEqualTo("Mon-Fri peak");
		assertThat(tactic1.get("{{tactic 1 weekends}}")).isEqualTo("Sat-Sun peak");
	}

	@Test
	void shouldForceDashForDaypartGenderWhenEstimateOffTest() {
		// Given: Batch B has values AND the sheet carries a manual male split, but the estimate is switched off
		GeneratePayload payload = new GeneratePayload(
				"brief", "standard", "",
				List.of(
						List.of("Media", "Comments"),
						List.of("Programmatic Display", ""),
						List.of("Tactic 1 male:", "70%")),
				List.of(), List.of(), List.of(), List.of(), List.of(),
				null, "", null, null, null, Boolean.FALSE);
		ClaudeTactical ccB = new ClaudeTactical(Map.of(1, new TacticInsight(60, 40, "Mon-Fri peak", "Sat-Sun peak")));

		// When: the tactic 1 section is built
		List<PreviewSection> sections = builder.buildSections(
				payload, emptyCampaignData(),
				claudeDefaults.emptyStrategic(), ccB, claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 1
		);

		// Then: all four tokens are a dash, ignoring both the Claude estimate and the manual sheet value
		Map<String, String> tactic1 = tacticValues(sections.get(4));
		assertThat(tactic1.get("{{tactic 1 male}}")).isEqualTo("—");
		assertThat(tactic1.get("{{tactic 1 female}}")).isEqualTo("—");
		assertThat(tactic1.get("{{tactic 1 weekdays}}")).isEqualTo("—");
		assertThat(tactic1.get("{{tactic 1 weekends}}")).isEqualTo("—");
	}

	private static Map<String, String> tacticValues(PreviewSection section) {
		return section.placeholders().stream()
				.collect(java.util.stream.Collectors.toMap(
						Placeholder::key, p -> p.value() == null ? "" : p.value()));
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

	@Test
	void shouldShowTheBookedFlightInFlightDatesAndTheSelectedWindowInTheReportingFilterTest() {
		// Given: an EOM report covering August of an October–December booked flight
		CampaignData data = new CampaignData(
				null, null, null, null, "Aug 1 – Aug 31, 2026",
				new FlightDates(java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 31)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), 1, 1,
				"Oct 1, 2025 – Dec 31, 2025", 2, 3, null);

		// When:
		List<PreviewSection> sections = builder.buildSections(
				minimalPayload(), data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 1);

		// Then: the two dates no longer say the same thing
		Map<String, String> start = valuesOf(sections.get(0));
		assertThat(start).containsEntry("{{flight_dates}}", "Oct 1, 2025 – Dec 31, 2025");
		assertThat(start).containsEntry("{{reporting filter}}", "Aug 1 – Aug 31, 2026");
	}

	@Test
	void shouldKeepFlightDatesOnTheConfirmedWindowWhenTheFlightIsUnknownTest() {
		// Given: an EOC report, where no media-plan flight is resolved
		CampaignData data = new CampaignData(
				null, null, null, null, "Jan 1 – Dec 31, 2025", null, null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), null);

		// When:
		List<PreviewSection> sections = builder.buildSections(
				minimalPayload(), data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 1);

		// Then: the token behaves exactly as it did before the split
		Map<String, String> start = valuesOf(sections.get(0));
		assertThat(start).containsEntry("{{flight_dates}}", "Jan 1 – Dec 31, 2025");
		assertThat(start).containsEntry("{{reporting filter}}", "Jan 1 – Dec 31, 2025");
	}

	/** The section's {@code token → value} pairs. */
	private Map<String, String> valuesOf(PreviewSection section) {
		return section.placeholders().stream()
				.filter(p -> p.value() != null)
				.collect(java.util.stream.Collectors.toMap(Placeholder::key, Placeholder::value));
	}
}
