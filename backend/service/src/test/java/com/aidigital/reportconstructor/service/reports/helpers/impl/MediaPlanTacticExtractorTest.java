package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.engine.TacticCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPlanTacticExtractorTest {

	private final MediaPlanTacticExtractorImpl extractor =
			new MediaPlanTacticExtractorImpl(new TacticCatalog(), new SheetRowHelperImpl());

	@Test
	void extract_skipsStopPhrasesAndNonWhitelist() {
		// Given: a flat plan with a totals row and a non-tactic row interleaved
		List<List<String>> plan = List.of(
				List.of("Media", "Comments"),
				List.of("Total media", ""),
				List.of("Programmatic Display", "note"),
				List.of("Not A Real Tactic", ""),
				List.of("Meta (CPM)", "")
		);

		// When
		List<String> names = extractor.extract(plan).stream().map(PlanTactic::name).toList();

		// Then: only recognised tactics survive, in sheet order
		assertThat(names).containsExactly("Programmatic Display", "Meta (CPM)");
	}

	@Test
	void extract_capturesGroupLabelAndRowContext() {
		// Given: a plan with a section label above the tactic and targeting on the tactic row
		List<List<String>> plan = List.of(
				List.of("Media", "Comments", "Targeting"),
				List.of("Grapevine Vintage Railroad", "", ""),
				List.of("Google SEM", "Even-paced", "Keyword-based")
		);

		// When
		List<PlanTactic> rows = extractor.extract(plan);

		// Then: the group label and the row's other cells are joined into the context
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst().name()).isEqualTo("Google SEM");
		assertThat(rows.getFirst().context()).contains("Grapevine Vintage Railroad", "Even-paced", "Keyword-based");
	}

	@Test
	void extract_keepsTacticBlocksAfterProductSubtotalRows() {
		// Given: a product-grouped media plan where "PRODUCT TOTALS" sub-total rows sit between the
		// tactic blocks — the collector used to stop at the first such row and drop everything below it
		List<List<String>> plan = List.of(
				List.of("", "Flight Start", "Flight End", "Geo", "Media", "Comments"),
				List.of("", "JARS", "", "", "", ""),
				List.of("", "2026-07-01", "2026-09-30", "See Geo", "Meta (CPM)", ""),
				List.of("", "2026-07-01", "2026-09-30", "See Geo", "Blended Set CTV/OTT", ""),
				List.of("", "PRODUCT TOTALS", "", "", "", ""),
				List.of("", "POUCHES", "", "", "", ""),
				List.of("", "2026-07-01", "2026-09-30", "See Geo", "Meta (CPM)", ""),
				List.of("", "PRODUCT TOTALS", "", "", "", ""),
				List.of("", "SNACKS", "", "", "", ""),
				List.of("", "2026-07-01", "2026-09-30", "See Geo", "Meta (CPM)", ""),
				List.of("", "Totals:", "", "", "", "")
		);

		// When
		List<PlanTactic> rows = extractor.extract(plan);

		// Then: every tactic block is captured, and the product group flows into each tactic's context
		assertThat(rows).extracting(PlanTactic::name)
				.containsExactly("Meta (CPM)", "Blended Set CTV/OTT", "Meta (CPM)", "Meta (CPM)");
		assertThat(rows.get(0).context()).contains("JARS");
		assertThat(rows.get(2).context()).contains("POUCHES");
		assertThat(rows.get(3).context()).contains("SNACKS");
	}

	@Test
	void extract_capsAtSevenTactics() {
		// Given: a plan with nine recognised tactic rows
		List<List<String>> plan = new java.util.ArrayList<>();
		plan.add(List.of("Media"));
		for (int i = 0; i < 9; i++) {
			plan.add(List.of("Meta (CPM)"));
		}

		// When
		List<PlanTactic> rows = extractor.extract(plan);

		// Then: extraction stops at the report's seven tactic slots
		assertThat(rows).hasSize(7);
	}
}
