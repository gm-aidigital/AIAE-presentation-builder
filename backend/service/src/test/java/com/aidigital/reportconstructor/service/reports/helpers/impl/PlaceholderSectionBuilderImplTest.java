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
	void shouldBuildThirtyNineSectionsWithExpectedTitlesTest() {
		GeneratePayload payload = minimalPayload();
		CampaignData data = emptyCampaignData();

		List<PreviewSection> sections = builder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 28
		);

		// 8 lead sections + 28 per-tactic sections + Funnel Channels + Optimization Recommendations
		// + Frequency Story = 39
		assertThat(sections).hasSize(39);
		assertThat(sections.get(0).title()).isEqualTo("Start");
		assertThat(sections.get(1).title()).isEqualTo("Overview Slides");
		assertThat(sections.get(2).title()).isEqualTo("Strategic Insights");
		assertThat(sections.get(3).title()).isEqualTo("Pacing Dashboard Takeaways");
		assertThat(sections.get(4).title()).isEqualTo("Performance Dashboard Takeaways");
		assertThat(sections.get(5).title()).isEqualTo("What We Did This Month");
		assertThat(sections.get(5).placeholders())
				.extracting(Placeholder::key)
				.startsWith("{{observation 1}}", "{{observation 1 text}}",
						"{{action 1}}", "{{action 1 text}}",
						"{{impact 1}}", "{{impact 1 text}}")
				.endsWith("{{impact 3}}", "{{impact 3 text}}");
		assertThat(sections.get(6).title()).isEqualTo("Focus Next Month");
		assertThat(sections.get(6).placeholders())
				.extracting(Placeholder::key)
				.startsWith("{{updated projection}}",
						"{{carry forward 1}}", "{{carry forward 2}}", "{{carry forward 3}}",
						"{{pivot 1}}", "{{pivot 2}}", "{{pivot 3}}")
				.endsWith("{{test 1}}", "{{test 2}}", "{{test 3}}");
		assertThat(sections.get(7).title()).isEqualTo("Summary Metrics");
		assertThat(sections.get(8).title()).isEqualTo("Tactic 1");
		assertThat(sections.get(35).title()).isEqualTo("Tactic 28");
		assertThat(sections.get(36).title()).isEqualTo("Funnel Channels");
		assertThat(sections.get(37).title()).isEqualTo("Optimization Recommendations");
		assertThat(sections.get(37).placeholders())
				.extracting(Placeholder::key)
				.containsExactly(
						"{{recommendation 1}}", "{{recommendation 1 text}}",
						"{{recommendation 2}}", "{{recommendation 2 text}}",
						"{{recommendation 3}}", "{{recommendation 3 text}}",
						"{{recommendation 4}}", "{{recommendation 4 text}}");
		assertThat(sections.get(38).title()).isEqualTo("Frequency Story");
		assertThat(sections.get(38).placeholders())
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

		assertThat(sections.get(8).placeholders()).hasSize(32);
		assertThat(sections.get(8).placeholders())
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

		assertThat(sections.get(14).placeholders()).hasSize(32);
		assertThat(sections.get(14).placeholders())
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

		// 8 lead sections + 2 per-tactic sections + Funnel Channels + Optimization Recommendations
		// + Frequency Story = 13
		assertThat(sections).hasSize(13);
		assertThat(sections.get(8).title()).isEqualTo("Tactic 1");
		assertThat(sections.get(9).title()).isEqualTo("Tactic 2");
		assertThat(sections.get(10).title()).isEqualTo("Funnel Channels");
		assertThat(sections.get(11).title()).isEqualTo("Optimization Recommendations");
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
		Map<String, String> tactic1 = tacticValues(sections.get(8));
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
		Map<String, String> tactic1 = tacticValues(sections.get(8));
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

	/**
	 * Builds the same payload as {@link #minimalPayload()} but typed as an end-of-month report, which is
	 * what opens the EOM-only block of the Summary Metrics section.
	 *
	 * @return an EOM generate payload
	 */
	private static GeneratePayload eomPayload() {
		return new GeneratePayload(
				"brief", "EOM", "",
				List.of(List.of("Media", "Comments"), List.of("Programmatic Display", "")),
				List.of(), List.of(), List.of(), List.of(), List.of(), null, "", null, null, null);
	}

	@Test
	void shouldCarryTheReportingMonthNumberSoTheWorkbooksMetricHeadersAreFilledTest() {
		// Given: an EOM report on month 2 of a 3-month flight. The workbook prints this number 57 times —
		// once in its own "Reporting month no." cell and once in each METRIC block's two column headers
		// ("MONTH {{mon no}} GOAL" / "MONTH {{mon no}} ACTUAL") — and every one of them is filled by this
		// single token, so it must never be absent from the map.
		CampaignData data = new CampaignData(
				null, null, null, null, "Aug 1 – Aug 31, 2026",
				new FlightDates(java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 31)),
				null, null, null, null, null,
				new Totals(0, 0, 0, 0, null, null), Map.of(), 1, 1,
				"Oct 1, 2025 – Dec 31, 2025", 2, 3, null);

		// When:
		List<PreviewSection> sections = builder.buildSections(
				eomPayload(), data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(),
				null, null, null, null, null,
				new CampaignFrequencies(null, null, null, null), 1);

		// Then: the month and the flight length both travel, so a header can never read "MONTH  GOAL"
		Map<String, String> totals = sections.stream()
				.filter(section -> "Summary Metrics".equals(section.title()))
				.findFirst()
				.map(this::valuesOf)
				.orElseThrow();
		assertThat(totals).containsEntry("{{mon no}}", "2");
		assertThat(totals).containsEntry("{{total mon no}}", "3");
	}

	/** The section's {@code token → value} pairs. */
	private Map<String, String> valuesOf(PreviewSection section) {
		return section.placeholders().stream()
				.filter(p -> p.value() != null)
				.collect(java.util.stream.Collectors.toMap(Placeholder::key, Placeholder::value));
	}
}
