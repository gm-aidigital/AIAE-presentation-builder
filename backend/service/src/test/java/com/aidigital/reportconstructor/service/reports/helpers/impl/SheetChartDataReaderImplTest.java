package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetChartDataReaderImplTest {

	private final SheetChartDataReaderImpl reader =
			new SheetChartDataReaderImpl(new SheetRowHelperImpl(), new ReportNumberParserImpl());

	@Test
	void shouldReadDailyAndMonthlyPivotsForCtrTacticTest() {
		// Given: a daily block offset four columns right, then a monthly block, for a CTR tactic.
		// The pacing writer overwrites the marker cells with the data, so the block is anchored
		// by its "Daily pacing 1" / "Monthly pacing 1" label and its Date/Impressions/Amount headers.
		List<List<String>> grid = List.of(
				List.of("", "", "", "", "Daily pacing 1"),
				List.of("", "", "", "", "Date", "Impressions", "Amount"),
				List.of("", "", "", "", "Jun 1", "1,000", "10"),
				List.of("", "", "", "", "Jun 2", "2,000", "20"),
				List.of(),
				List.of("Monthly pacing 1"),
				List.of("Date", "Impressions", "Amount"),
				List.of("Jun 2026", "3,000", "30"));

		// When: the pivots are read with a CTR KPI type
		SheetChartData out = reader.read(grid, 1, Map.of(1, "ctr"));

		// Then: the daily series lands in the clicks slot and flags clicks
		Pivot daily = out.dailyPivots().get(1);
		assertThat(daily.data().get("Jun 1")).containsExactly(1000.0, 10.0, 0.0);
		assertThat(daily.data().get("Jun 2")).containsExactly(2000.0, 20.0, 0.0);
		assertThat(daily.hasClicks()).isTrue();
		assertThat(daily.hasCompletions()).isFalse();
		// And: the monthly series is read from its own anchored block
		Pivot monthly = out.monthlyPivots().get(1);
		assertThat(monthly.data().get("Jun 2026")).containsExactly(3000.0, 30.0, 0.0);
		assertThat(monthly.hasClicks()).isTrue();
	}

	@Test
	void shouldMapMetricToCompletionsForVcrTacticTest() {
		// Given: a daily block for a VCR (video) tactic
		List<List<String>> grid = List.of(
				List.of("Daily pacing 1"),
				List.of("Date", "Impressions", "Amount"),
				List.of("Jun 1", "5,000", "4,000"));

		// When: the pivots are read with a VCR KPI type
		SheetChartData out = reader.read(grid, 1, Map.of(1, "vcr"));

		// Then: the metric lands in the completions slot and flags completions, not clicks
		Pivot daily = out.dailyPivots().get(1);
		assertThat(daily.data().get("Jun 1")).containsExactly(5000.0, 0.0, 4000.0);
		assertThat(daily.hasCompletions()).isTrue();
		assertThat(daily.hasClicks()).isFalse();
	}

	@Test
	void shouldReadOnlyTheAnchoredTacticsColumnsWhenTwoBlocksShareRowsTest() {
		// Given: tactic 1's daily block sits left of tactic 2's, both sharing the same rows, so
		// tactic 1's search window overlaps tactic 2's columns
		List<List<String>> grid = List.of(
				List.of("Daily pacing 1", "", "", "Daily pacing 2"),
				List.of("Date", "Impressions", "Amount", "Date", "Impressions", "Amount"),
				List.of("Jun 1", "1,000", "10", "Jun 1", "9,000", "8,000"));

		// When: both tactics are read (tactic 1 CTR, tactic 2 VCR)
		SheetChartData out = reader.read(grid, 2, Map.of(1, "ctr", 2, "vcr"));

		// Then: each tactic resolves to its own leftmost columns, unaffected by the neighbour's
		assertThat(out.dailyPivots().get(1).data().get("Jun 1")).containsExactly(1000.0, 10.0, 0.0);
		assertThat(out.dailyPivots().get(2).data().get("Jun 1")).containsExactly(9000.0, 0.0, 8000.0);
	}

	@Test
	void shouldReturnEmptyPivotWhenTacticBlockAbsentTest() {
		// Given: a grid holding only tactic 1's daily block, but two tactics requested
		List<List<String>> grid = List.of(
				List.of("Daily pacing 1"),
				List.of("Date", "Impressions", "Amount"),
				List.of("Jun 1", "1,000", "10"));

		// When: two tactics are read
		SheetChartData out = reader.read(grid, 2, Map.of(1, "ctr", 2, "ctr"));

		// Then: tactic 1 has data and tactic 2's absent block yields an empty pivot
		assertThat(out.dailyPivots().get(1).isEmpty()).isFalse();
		assertThat(out.dailyPivots().get(2).isEmpty()).isTrue();
		assertThat(out.monthlyPivots().get(2).isEmpty()).isTrue();
	}

	@Test
	void shouldReturnEmptyDataForNullGridTest() {
		// Given: no grid

		// When: the reader is asked to read one tactic
		SheetChartData out = reader.read(null, 1, Map.of(1, "ctr"));

		// Then: both pivot maps are empty
		assertThat(out.dailyPivots()).isEmpty();
		assertThat(out.monthlyPivots()).isEmpty();
	}
}
