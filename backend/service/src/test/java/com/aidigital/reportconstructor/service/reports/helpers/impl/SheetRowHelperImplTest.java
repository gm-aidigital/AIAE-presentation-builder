package com.aidigital.reportconstructor.service.reports.helpers.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SheetRowHelperImplTest {

	private final SheetRowHelperImpl helper = new SheetRowHelperImpl();

	@Test
	void shouldCollectDistinctColumnValuesBelowHeaderSkippingSectionRowTest() {
		// Given: a grid where the header is followed by a section-title row, then repeated line-item values
		List<List<String>> rows = List.of(
				List.of("Flight", "Geo", "Media"),
				List.of("Evergreen", "", ""),
				List.of("2026-06-08", "Texas", "Programmatic Display"),
				List.of("2026-06-08", "Oklahoma", "Google SEM"),
				List.of("2026-06-08", "Texas", "Meta"));

		// When: the geo column is collected
		List<String> values = helper.collectColumnValuesBelow(rows, Set.of("geo"));

		// Then: distinct values are returned in first-seen order, ignoring the empty section-row cell
		assertThat(values).containsExactly("Texas", "Oklahoma");
	}

	@Test
	void shouldStopColumnCollectionAtTotalsFooterRowTest() {
		// Given: a totals row sits after the data and before unrelated trailing rows
		List<List<String>> rows = List.of(
				List.of("Goal", "Cost"),
				List.of("Awareness", "100"),
				List.of("Totals:", "100"),
				List.of("Consideration", "999"));

		// When: the goal column is collected
		List<String> values = helper.collectColumnValuesBelow(rows, Set.of("goal"));

		// Then: collection stops at the totals row and ignores rows beyond it
		assertThat(values).containsExactly("Awareness");
	}

	@Test
	void shouldMatchHeaderSynonymIgnoringCaseAndPunctuationTest() {
		// Given: the header cell uses mixed case and a line break
		List<List<String>> rows = List.of(
				List.of("Targeted\nLocations"),
				List.of("Dallas-Fort Worth"));

		// When: matched against the normalised synonym
		List<String> values = helper.collectColumnValuesBelow(rows, Set.of("targeted locations"));

		// Then: the column value is collected
		assertThat(values).containsExactly("Dallas-Fort Worth");
	}

	@Test
	void shouldReturnEmptyWhenNoHeaderMatchesTest() {
		// Given: a grid with no matching header
		List<List<String>> rows = List.of(List.of("Campaign:", "Spring"));

		// When-Then: no column is found
		assertThat(helper.collectColumnValuesBelow(rows, Set.of("geo"))).isEmpty();
	}

	@Test
	void shouldReportGenericSeeTabPointerAsGeoTabReferenceTest() {
		// Given-When-Then: both the classic and generic "see ... tab/sheet" pointers are detected
		assertThat(helper.referencesGeoTab("See Geo Tab")).isTrue();
		assertThat(helper.referencesGeoTab("see locations sheet")).isTrue();
		assertThat(helper.referencesGeoTab("Texas, Oklahoma")).isFalse();
		assertThat(helper.referencesGeoTab(null)).isFalse();
	}
}
