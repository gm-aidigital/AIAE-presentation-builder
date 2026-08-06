package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.DateFilterMode;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportResumeStateHelperImplTest {

	private final ReportResumeStateHelperImpl helper =
			new ReportResumeStateHelperImpl(new ObjectMapper().registerModule(new JavaTimeModule()));

	@Test
	void shouldKeepOnlyTheFieldsTheSlidesStepStillNeedsTest() {
		// Given: a full generation payload, including the heavy source grids
		GeneratePayload payload = new GeneratePayload(
				"the brief", "EOM", "1.2M",
				List.of(List.of("plan")), List.of(List.of("raw")), List.of(), List.of(), List.of(List.of("geo")),
				List.of(), List.of(new BreakdownSelection(1, List.of("tp", "geo"))),
				"bq-sheet", new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
				null, "the change log", Boolean.FALSE);

		// When: it is distilled into the resume state
		ReportResumeState state = helper.toState(payload, null);

		// Then: the scalars survive and the grids are simply absent
		assertThat(state.reportType()).isEqualTo("EOM");
		assertThat(state.brief()).isEqualTo("the brief");
		assertThat(state.changeLog()).isEqualTo("the change log");
		assertThat(state.marketVolume()).isEqualTo("1.2M");
		assertThat(state.estimateDaypartGender()).isFalse();
		assertThat(state.dateFilter().start()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(state.breakdownSelections()).singleElement()
				.extracting(BreakdownSelection::breakdowns).isEqualTo(List.of("tp", "geo"));
		assertThat(state.tacticNames()).isNull();
	}

	@Test
	void shouldRecordTacticNamesInReportOrderTest() {
		// Given: campaign data whose tactics arrive out of order
		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		tactics.put(2, tactic("CTV"));
		tactics.put(1, tactic("Display"));
		CampaignData data = new CampaignData(
				"Client", "Campaign", "US", "Awareness", "1 Jul – 31 Jul", null, "$100,000",
				"CTR", "CTV, Display", "25-54", "Auto intenders", null, tactics, null);

		// When: the state is built
		ReportResumeState state = helper.toState(
				new GeneratePayload(null, "EOC", null, List.of(), List.of(), List.of(), List.of(), List.of(),
						null, null, null, null, null, null, null),
				data);

		// Then: the names follow the tactic numbers, not the map's insertion order
		assertThat(state.tacticNames()).containsExactly("Display", "CTV");
	}

	/**
	 * Builds a minimal tactic carrying nothing but its name.
	 *
	 * @param name the tactic name
	 * @return the tactic
	 */
	Tactic tactic(String name) {
		return new Tactic(name, "display", null, 0, 0, 0, 0,
				null, null, null, null, null, null, null, null, null, null, null, null);
	}

	@Test
	void shouldRoundTripThroughJsonTest() {
		// Given: a state carrying a date window and per-tactic toggles
		ReportResumeState state = new ReportResumeState(
				"EOC", "brief", "log", "500K",
				new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 4)),
				Boolean.TRUE, List.of(new BreakdownSelection(3, List.of("dev"))), List.of("Audio"));

		// When: it is serialised and read back
		ReportResumeState parsed = helper.parse(helper.serialize(state));

		// Then: it survives unchanged
		assertThat(parsed).isEqualTo(state);
	}

	@Test
	void shouldParseToAnEmptyStateWhenNothingUsableIsStoredTest() {
		// Given: jobs with no stored payload and with an unparseable one

		// When-Then: neither throws, and both yield an all-null state
		assertThat(helper.parse(null).reportType()).isNull();
		assertThat(helper.parse("  ").breakdownSelections()).isNull();
		assertThat(helper.parse("{not-json").tacticNames()).isNull();
	}

	@Test
	void shouldReturnEmptyStateForANullPayloadTest() {
		// Given-When: a run with no payload at all
		ReportResumeState state = helper.toState(null, null);

		// Then: the state is empty rather than absent, so callers never null-check it
		assertThat(state).isNotNull();
		assertThat(state.brief()).isNull();
	}
}
