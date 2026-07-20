package com.aidigital.reportconstructor.externalservices.anthropic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbookGeoFilterTest {

	@Test
	void shouldKeepOnlyGeographyRowsAndTheirTabMarkerTest() {
		// Given: a two-tab workbook where only one row mentions geography
		WorkbookGeoFilter filter = new WorkbookGeoFilter();
		List<List<String>> workbook = List.of(
				List.of("### TAB: Proposal ###"),
				List.of("Budget", "$500,000"),
				List.of("Target DMAs", "Dallas, Austin"),
				List.of("### TAB: Creative specs ###"),
				List.of("Banner", "300x250"));

		// When:
		List<String> kept = filter.keepGeoRows(workbook);

		// Then: the geo row survives with its own tab marker; the creative tab contributes nothing
		assertThat(kept).containsExactly("### TAB: Proposal ###", "Target DMAs | Dallas, Austin");
	}

	@Test
	void shouldMatchGeographyWordsOnWordBoundariesOnlyTest() {
		// Given: rows whose text merely contains a geography word as a substring
		WorkbookGeoFilter filter = new WorkbookGeoFilter();
		List<List<String>> workbook = List.of(
				List.of("Real estate vertical"),
				List.of("Marketing objectives"),
				List.of("State", "Texas"));

		// When:
		List<String> kept = filter.keepGeoRows(workbook);

		// Then: "estate" and "Marketing" do not count as geography; the real state row does
		assertThat(kept).containsExactly("State | Texas");
	}

	@Test
	void shouldReturnEmptyWhenWorkbookIsNullOrHasNoGeographyRowTest() {
		// Given:
		WorkbookGeoFilter filter = new WorkbookGeoFilter();
		List<List<String>> workbook = new ArrayList<>(Arrays.asList(
				List.of("### TAB: Estimates ###"),
				(List<String>) null,
				List.of("Impressions", "10,000,000")));

		// When:
		List<String> fromNull = filter.keepGeoRows(null);
		List<String> fromWorkbook = filter.keepGeoRows(workbook);

		// Then: a tab marker alone never survives, and a null grid is not an error
		assertThat(fromNull).isEmpty();
		assertThat(fromWorkbook).isEmpty();
	}
}
