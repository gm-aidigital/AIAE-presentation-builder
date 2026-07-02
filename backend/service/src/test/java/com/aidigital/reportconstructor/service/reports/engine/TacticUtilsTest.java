package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TacticUtilsTest {

	private final TacticExtractionHelper tacticUtils = ReportsEngineTestSupport.tacticExtractionHelper();
	private final TacticCatalog catalog = ReportsEngineTestSupport.tacticCatalog();

	@Test
	void freqFromMax_isDeterministicByTacticIndex() {
		assertEquals(9.0, tacticUtils.freqFromMax(1, 10.0));
		assertEquals(9.6, tacticUtils.freqFromMax(2, 10.0));
	}

	@Test
	void getTacticKpiType_videoTacticsReturnVcr() {
		assertEquals("vcr", tacticUtils.getTacticKpiType("programmatic ctv"));
		assertEquals("ctr", tacticUtils.getTacticKpiType("programmatic display"));
	}

	@Test
	void extractTacticsFromMedia_stopsAtTotalsRow() {
		List<List<String>> rows = List.of(
				List.of("x", "Media", "y"),
				List.of("", "Programmatic Display", ""),
				List.of("", "Meta (CPM)", ""),
				List.of("Totals", "", "")
		);
		assertThat(tacticUtils.extractTacticsFromMedia(rows))
				.containsExactly("Programmatic Display", "Meta (CPM)");
	}

	@Test
	void extractTacticsFromMedia_skipsSectionLabelsAndAddedValueRows() {
		// Given: a grouped media plan (no "Proposal" tab) where each tactic sits under
		// a section-label row with an empty Media cell, followed by added-value rows.
		List<List<String>> rows = List.of(
				List.of("", "Flight Start", "Flight End", "Geo", "Media", "Comments"),
				List.of("", "Pool Renovation", "", "", "", ""),
				List.of("", "2026-04-06", "2026-06-30", "See Geo", "Programmatic Display", ""),
				List.of("", "New Pool", "", "", "", ""),
				List.of("", "2026-04-06", "2026-06-30", "See Geo", "Programmatic Display", ""),
				List.of("", "Added Value Reports", "", "", "", ""),
				List.of("", "2026-04-06", "2026-06-30", "", "AI Digital Insights Reporting", ""),
				List.of("", "Totals:", "", "", "", "")
		);

		// When: extracting the Media-column tactics.
		List<String> tactics = tacticUtils.extractTacticsFromMedia(rows);

		// Then: only the recognised tactics are kept, in sheet order.
		assertThat(tactics).containsExactly("Programmatic Display", "Programmatic Display");
	}

	@Test
	void normalizeTacticDisplayName_mapsCtvAlias() {
		assertThat(tacticUtils.normalizeTacticDisplayName("programmatic ctv")).isEqualTo("CTV");
		assertThat(catalog.displayFor("unknown tactic")).isEqualTo("unknown tactic");
	}

	@Test
	void countTacticsInMediaPlan_countsWhitelistMatchesOnly() {
		List<List<String>> rows = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("not a tactic"),
				List.of("meta (cpm)")
		);
		assertThat(tacticUtils.countTacticsInMediaPlan(rows)).isEqualTo(2);
	}
}
