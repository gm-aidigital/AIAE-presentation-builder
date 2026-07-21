package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.engine.ChartPivot;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetPacingTableWriterTest {

	private final SheetPacingTableWriter writer = new SheetPacingTableWriter(
			Mockito.mock(ChartPivot.class), new TacticLineItemGrouper(), new ChartSheetWriter());

	@Test
	void findNumberedAnchorsTest_locatesEachTacticsOwnAnchorCell() {
		// Given: a grid with two tactics' "Daily pacing N" anchors on the same row
		List<List<Object>> grid = gridOf(
				List.of("", "Daily pacing 1", "", "Daily pacing 2"),
				List.of("Date", "Impressions", "Date", "Impressions"));

		// When:
		Map<Integer, int[]> anchors = writer.findNumberedAnchors(grid, SheetPacingTableWriter.DAILY_ANCHOR);

		// Then:
		assertThat(anchors.get(1)).containsExactly(0, 1);
		assertThat(anchors.get(2)).containsExactly(0, 3);
	}

	@Test
	void findPacingColumnsTest_resolvesOnlyTheRequestedTacticsColumnsWhenTwoTacticsShareARow() {
		// Given: tactic 1's daily block sits left of tactic 2's, both on row 3 (0-based)
		List<List<Object>> grid = gridOf(
				List.of("Daily pacing 1", "", "", "", "Daily pacing 2"),
				List.of("Date", "Impressions", "", "Date", "Impressions"),
				List.of("Date", "Impressions", "", "Date", "Impressions"),
				List.of("{{tactic 1 date}}", "{{tactic 1 impressions}}", "{{tactic 1 amount}}",
						"{{tactic 2 date}}", "{{tactic 2 impressions}}"));
		int[] anchor1 = {0, 0};
		int[] anchor2 = {0, 4};

		// When:
		PacingColumns cols1 = writer.findPacingColumns(grid, anchor1, 1, false);
		PacingColumns cols2 = writer.findPacingColumns(grid, anchor2, 2, false);

		// Then: tactic 1 resolves to its own columns, unaffected by tactic 2's tokens on the same row
		assertThat(cols1.dateCol()).isEqualTo(0);
		assertThat(cols1.impsCol()).isEqualTo(1);
		assertThat(cols1.metricCol()).isEqualTo(2);
		assertThat(cols1.dataStartRow()).isEqualTo(3);
		assertThat(cols2.dateCol()).isEqualTo(3);
		assertThat(cols2.impsCol()).isEqualTo(4);
	}

	@Test
	void findPacingColumnsTest_monthlyTokensDoNotMatchPlainDailyPattern() {
		// Given: only a "date mon" token is present
		List<List<Object>> grid = gridOf(
				List.of("Monthly pacing 1"),
				List.of("{{tactic 1 date mon}}"));
		int[] anchor = {0, 0};

		// When:
		PacingColumns daily = writer.findPacingColumns(grid, anchor, 1, false);
		PacingColumns monthly = writer.findPacingColumns(grid, anchor, 1, true);

		// Then:
		assertThat(daily.dateCol()).isEqualTo(-1);
		assertThat(monthly.dateCol()).isEqualTo(0);
	}

	@Test
	void findDistributionColumnsTest_skipsTheSectionTitleAndFindsTheSliceRowBelowTheAnchor() {
		// Given: the same {{tactic 1}} token appears both as the section title (row 0) and the
		// distribution block's slice label (row 3), directly below the "Channel Distribution 1" anchor
		List<List<Object>> grid = gridOf(
				List.of("{{tactic 1}}"),
				List.of("Channel Distribution 1"),
				List.of("Distribution", "Impressions"),
				List.of("{{tactic 1}}"),
				List.of("Total"));
		int[] anchor = {1, 0};

		// When:
		DistributionColumns cols = writer.findDistributionColumns(grid, anchor, 1, "Display Prospecting");

		// Then:
		assertThat(cols.tacticRow()).isEqualTo(3);
		assertThat(cols.otherRow()).isEqualTo(4);
		assertThat(cols.labelCol()).isEqualTo(0);
	}

	@Test
	void findDistributionColumnsTest_matchesTheSliceRowByResolvedTacticNameWhenTheTokenWasAlreadyReplaced() {
		// Given: on the SHEET target the earlier find/replace pass already swapped {{tactic 1}}
		// for the tactic name, so the slice label cell now holds the resolved name, not the token
		List<List<Object>> grid = gridOf(
				List.of("Channel Distribution 1"),
				List.of("Distribution", "Impressions"),
				List.of("Display Prospecting"),
				List.of("Total"));
		int[] anchor = {0, 0};

		// When:
		DistributionColumns cols = writer.findDistributionColumns(grid, anchor, 1, "Display Prospecting");

		// Then:
		assertThat(cols.tacticRow()).isEqualTo(2);
		assertThat(cols.otherRow()).isEqualTo(3);
		assertThat(cols.labelCol()).isEqualTo(0);
	}

	@Test
	void collectDistributionBlockTest_recordsAnErrorWhenTheAnchorIsMissing() {
		// Given:
		List<java.util.List<Object>> grid = gridOf(List.of("nothing here"));
		List<com.google.api.services.sheets.v4.model.ValueRange> data = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// When:
		writer.collectDistributionBlock(data, grid, null, "Sheet1", 3, "Tactic 3", 100.0, 900.0, errors);

		// Then:
		assertThat(data).isEmpty();
		assertThat(errors).containsExactly("Channel Distribution 3: anchor not found");
	}

	@SafeVarargs
	private static List<List<Object>> gridOf(List<String>... rows) {
		List<List<Object>> grid = new ArrayList<>();
		for (List<String> row : rows) {
			grid.add(new ArrayList<>(row));
		}
		return grid;
	}
}
