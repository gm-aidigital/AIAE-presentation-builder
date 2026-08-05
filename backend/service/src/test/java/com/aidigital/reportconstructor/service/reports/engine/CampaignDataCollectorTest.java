package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.DateFilterMode;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.RateType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignDataCollectorTest {

	private final CampaignDataCollector collector = ReportsEngineTestSupport.campaignDataCollector();

	@Test
	void collect_adjOverridesSheetForClientAndCampaign() {
		List<List<String>> sheet = List.of(
				List.of("Client name:", "Sheet Client"),
				List.of("Campaign:", "Sheet Campaign"),
				List.of("Media"),
				List.of("Programmatic Display")
		);
		List<List<String>> adj = List.of(
				List.of("Client name:", "Adj Client"),
				List.of("Campaign:", "Adj Campaign")
		);
		List<List<String>> bq = List.of(
				List.of("Date", "Channel", "Cost", "Impressions", "Clicks"),
				List.of("2026-03-01", "Display", "50", "1000", "10")
		);

		CampaignData data = collector.collect(sheet, adj, List.of(), List.of(), List.of(), null, "EOC");

		assertThat(data.client()).isEqualTo("Adj Client");
		assertThat(data.campaign()).isEqualTo("Adj Campaign");

		CampaignData bqOnly = collector.collect(List.of(), bq, List.of(), List.of(), List.of(), null, "EOC");
		assertThat(bqOnly.totals()).isNotNull();
		assertThat(bqOnly.totals().imps()).isEqualTo(1000);
	}

	@Test
	void collect_repeatedTacticNameGetsItsOwnEstimatesLineItemInOrder() {
		// Given: a media plan repeating "Display" twice around one "Audio", and an Estimates tab carrying a
		// distinct planned spend/impressions per line item in the same top-to-bottom order
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("programmatic audio"),
				List.of("programmatic display")
		);
		List<List<String>> estimates = List.of(
				List.of("Media", "Total Cost", "Impressions"),
				List.of("programmatic display", "100000", "500000"),
				List.of("programmatic audio", "200000", "700000"),
				List.of("programmatic display", "300000", "900000")
		);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), estimates, List.of(), null, "EOC");

		// Then: each occurrence keeps its own line item's figures rather than all Displays collapsing onto one
		assertThat(data.tactics().get(1).planSpend()).isEqualTo(100000.0);
		assertThat(data.tactics().get(1).planImps()).isEqualTo(500000.0);
		assertThat(data.tactics().get(2).planSpend()).isEqualTo(200000.0);
		assertThat(data.tactics().get(2).planImps()).isEqualTo(700000.0);
		assertThat(data.tactics().get(3).planSpend()).isEqualTo(300000.0);
		assertThat(data.tactics().get(3).planImps()).isEqualTo(900000.0);
	}

	@Test
	void collectShouldReadTheMediaPlansWeeklyFrequencyColumnPerTacticTest() {
		// Given: a media plan whose Estimates tab carries both frequency columns, the per-week one and
		// the flight cap, with a different weekly figure per line item
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("programmatic audio")
		);
		List<List<String>> estimates = List.of(
				List.of("Media", "Total Cost", "Impressions", "Frequency per week", "Max frequency"),
				List.of("programmatic display", "100000", "500000", "1", "12"),
				List.of("programmatic audio", "200000", "700000", "2.5", "20")
		);
		DateFilter dateFilter = new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 7, 1),
				LocalDate.of(2026, 7, 31));

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), estimates,
				List.of(new LineItemMapping("Programmatic Display", null, 1, RateType.CPM, 10.0, 3100.0),
						new LineItemMapping("Programmatic Audio", null, 2, RateType.CPM, 8.0, 1600.0)),
				dateFilter, "EOM");

		// Then: each tactic keeps its own weekly frequency alongside the untouched max-frequency cap
		assertThat(data.tactics().get(1).planWeeklyFreq()).isEqualTo(1.0);
		assertThat(data.tactics().get(1).planMaxFreq()).isEqualTo(12.0);
		assertThat(data.tactics().get(2).planWeeklyFreq()).isEqualTo(2.5);
		assertThat(data.tactics().get(2).planMaxFreq()).isEqualTo(20.0);
	}

	@Test
	void collect_buildsTacticMapFromMediaColumn() {
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("Tactic 1:", "Display Tactic")
		);
		CampaignData data = collector.collect(
				sheet, List.of(), List.of(), List.of(),
				List.of(new LineItemMapping("Display Tactic", "111", 1)), null, "EOC");

		assertThat(data.tactics()).containsKey(1);
		assertThat(data.tactics().get(1).name()).contains("Display");
	}

	@Test
	void collectForEomShouldResolvePlanFromRateAndMonthlyBudgetTest() {
		// Given: a CPM tactic with a $3,100 monthly budget and $10 unit price — an EOM report always
		// covers exactly one reporting month, so the monthly budget itself is the spend target, with no
		// flight-length multiplier
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		DateFilter dateFilter = new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 3, 1),
				LocalDate.of(2026, 3, 31));
		LineItemMapping mapping = new LineItemMapping("Programmatic Display", null, 1, RateType.CPM, 10.0, 3100.0);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), List.of(), List.of(mapping), dateFilter,
				"EOM");

		// Then: Estimates-sourced fields stay null, and the rate/budget-derived spend/imps are the
		// monthly figures unscaled: 3100 spend, 3100 / 10 * 1000 = 310,000 impressions
		assertThat(data.tactics().get(1).planSpend()).isEqualTo(3100.0);
		assertThat(data.tactics().get(1).planImps()).isEqualTo(310_000.0);
		assertThat(data.tactics().get(1).planCtr()).isNull();
		assertThat(data.tactics().get(1).planVcr()).isNull();
		assertThat(data.eomMonthNumber()).isEqualTo(1);
		assertThat(data.eomFlightMonthsTotal()).isEqualTo(1);
	}

	@Test
	void collectForEomShouldResolvePlanEvenWithoutFlightDatesSetTest() {
		// Given: the same rate/budget mapping, but no Flight dates entered at Data Inputs — the monthly
		// budget is the plan regardless, since it is never multiplied by the flight window's length
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		LineItemMapping mapping = new LineItemMapping("Programmatic Display", null, 1, RateType.CPM, 10.0, 3100.0);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), List.of(), List.of(mapping), null, "EOM");

		// Then:
		assertThat(data.tactics().get(1).planSpend()).isEqualTo(3100.0);
		assertThat(data.tactics().get(1).planImps()).isEqualTo(310_000.0);
		assertThat(data.eomFlightMonthsTotal()).isNull();
	}

	@Test
	void collectForEomShouldLeavePlanUnresolvedWhenMonthlyBudgetIsMissingTest() {
		// Given: a mapping with a rate type and unit price but no monthly budget entered yet
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		LineItemMapping mapping = new LineItemMapping("Programmatic Display", null, 1, RateType.CPM, 10.0, null);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), List.of(), List.of(mapping), null, "EOM");

		// Then:
		assertThat(data.tactics().get(1).planSpend()).isNull();
		assertThat(data.tactics().get(1).planImps()).isNull();
	}

	@Test
	void resolveEomPlanShouldFillAllThreeUnitPlansForEveryRateTypeTest() {
		// Given: three tactics bought on CPM, CPC and CPV, each with the same 0.20% CTR / 50% VCR
		// benchmarks from the Estimates tab (row layout {spend, imps, ctr, vcr, maxFreq})
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Display", "li-1", 1, RateType.CPM, 10.0, 3100.0),
				new LineItemMapping("Search", "li-2", 2, RateType.CPC, 2.0, 500.0),
				new LineItemMapping("Video", "li-3", 3, RateType.CPV, 0.05, 300.0));
		Map<Integer, double[]> estimates = Map.of(
				1, new double[]{Double.NaN, Double.NaN, 0.20, 50.0, Double.NaN},
				2, new double[]{Double.NaN, Double.NaN, 0.20, 50.0, Double.NaN},
				3, new double[]{Double.NaN, Double.NaN, 0.20, 50.0, Double.NaN});

		// When:
		Map<Integer, double[]> plan = collector.resolveEomPlanByTacticNum(mapping, estimates);

		// Then: each row carries impressions, clicks and completions (indices 1, 5 and 6) — the bought
		// unit from the rate and budget, the other two derived through the CTR/VCR benchmarks
		assertThat(plan.get(1)[1]).isEqualTo(310_000.0);
		assertThat(plan.get(1)[5]).isEqualTo(620.0);
		assertThat(plan.get(1)[6]).isEqualTo(155_000.0);
		assertThat(plan.get(2)[5]).isEqualTo(250.0);
		assertThat(plan.get(2)[1]).isEqualTo(125_000.0);
		assertThat(plan.get(2)[6]).isEqualTo(62_500.0);
		assertThat(plan.get(3)[6]).isEqualTo(6_000.0);
		assertThat(plan.get(3)[1]).isEqualTo(12_000.0);
		assertThat(plan.get(3)[5]).isEqualTo(24.0);
	}

	@Test
	void collectForEocShouldIgnoreLineItemMappingRateFieldsTest() {
		// Given: the same rate/budget fields and Flight dates as the EOM case, but reportType is EOC
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		DateFilter dateFilter = new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 3, 31));
		LineItemMapping mapping = new LineItemMapping("Programmatic Display", null, 1, RateType.CPM, 10.0, 3100.0);

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), List.of(), List.of(mapping), dateFilter,
				"EOC");

		// Then: no Estimates row exists for this tactic, so plan stays unresolved — the rate/budget
		// fields on the mapping are never consulted for EOC
		assertThat(data.tactics().get(1).planSpend()).isNull();
		assertThat(data.tactics().get(1).planImps()).isNull();
		assertThat(data.eomMonthNumber()).isNull();
		assertThat(data.eomFlightMonthsTotal()).isNull();
	}

	@Test
	void collectShouldRenumberTheReportAroundTheExcludedPlanRowsTest() {
		// Given: a plan repeating "Display" around one "Audio", with the Audio line dropped at matching
		// time — the two surviving rows arrive renumbered 1..2, each pinned to its own plan position
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("programmatic audio"),
				List.of("programmatic display")
		);
		List<List<String>> estimates = List.of(
				List.of("Media", "Total Cost", "Impressions"),
				List.of("programmatic display", "100000", "500000"),
				List.of("programmatic audio", "200000", "700000"),
				List.of("programmatic display", "300000", "900000")
		);
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1),
				new LineItemMapping("Programmatic Display", "li-3", 2, null, null, null, 3));

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), estimates, mapping, null, "EOC");

		// Then: the report has exactly two tactics, and the second one carries the third plan row's
		// figures — the dropped Audio row does not hand its Estimates line to the Display below it
		assertThat(data.tactics()).containsOnlyKeys(1, 2);
		assertThat(data.tactics().get(1).planSpend()).isEqualTo(100000.0);
		assertThat(data.tactics().get(2).planSpend()).isEqualTo(300000.0);
		assertThat(data.tactics().get(2).planImps()).isEqualTo(900000.0);
		assertThat(data.tacticsList()).doesNotContain("Audio");
	}

	@Test
	void collectShouldKeepUnmappedDeliveryOutOfTheCampaignTotalsTest() {
		// Given: three delivered line items, only two of which the user kept on the matching screen
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("programmatic audio")
		);
		List<List<String>> bq = List.of(
				List.of("Date", "Channel", "Cost", "Impressions", "Clicks", "Line Item ID"),
				List.of("2026-03-01", "Display", "50", "1000", "10", "li-1"),
				List.of("2026-03-01", "Audio", "70", "2000", "20", "li-2"),
				List.of("2026-03-01", "Video", "90", "4000", "40", "li-9")
		);
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1),
				new LineItemMapping("Programmatic Audio", "li-2", 2, null, null, null, 2));

		// When:
		CampaignData data = collector.collect(sheet, bq, List.of(), List.of(), mapping, null, "EOC");

		// Then: the unclaimed line item is absent from the campaign totals, which add up to exactly the
		// two reported tactics
		assertThat(data.totals().spend()).isEqualTo(120.0);
		assertThat(data.totals().imps()).isEqualTo(3000.0);
		assertThat(data.totals().clicks()).isEqualTo(30.0);
	}

	@Test
	void collectShouldStillCountEveryDeliveryRowWhenNothingWasMatchedTest() {
		// Given: the same export with no line-item mapping at all (nothing was matched)
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		List<List<String>> bq = List.of(
				List.of("Date", "Channel", "Cost", "Impressions", "Clicks", "Line Item ID"),
				List.of("2026-03-01", "Display", "50", "1000", "10", "li-1"),
				List.of("2026-03-01", "Video", "90", "4000", "40", "li-9")
		);

		// When:
		CampaignData data = collector.collect(sheet, bq, List.of(), List.of(), List.of(), null, "EOC");

		// Then: totals cover the whole export, as before matching existed
		assertThat(data.totals().spend()).isEqualTo(140.0);
		assertThat(data.totals().imps()).isEqualTo(5000.0);
	}

	@Test
	void collectShouldFallBackToTheWholeExportWhenNoRowResolvesToAMappedLineItemTest() {
		// Given: an export whose delivery rows carry no line-item id at all, matched against a mapping
		// that does (a naming format this parser cannot read, or an export from another campaign)
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display")
		);
		List<List<String>> bq = List.of(
				List.of("Date", "Channel", "Cost", "Impressions", "Clicks"),
				List.of("2026-03-01", "Display", "50", "1000", "10"),
				List.of("2026-03-02", "Display", "70", "2000", "20")
		);
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1));

		// When:
		CampaignData data = collector.collect(sheet, bq, List.of(), List.of(), mapping, null, "EOC");

		// Then: the campaign totals still describe the export instead of collapsing to zero
		assertThat(data.totals().spend()).isEqualTo(120.0);
		assertThat(data.totals().imps()).isEqualTo(3000.0);
	}

	@Test
	void collectShouldKeepEstimatesAlignedWhenNoRowWasExcludedTest() {
		// Given: the repeated-name plan with a full mapping — nothing excluded, so every tactic must
		// keep exactly the Estimates line its own plan row carries
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("programmatic audio"),
				List.of("programmatic display")
		);
		List<List<String>> estimates = List.of(
				List.of("Media", "Total Cost", "Impressions"),
				List.of("programmatic display", "100000", "500000"),
				List.of("programmatic audio", "200000", "700000"),
				List.of("programmatic display", "300000", "900000")
		);
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1),
				new LineItemMapping("Programmatic Audio", "li-2", 2, null, null, null, 2),
				new LineItemMapping("Programmatic Display", "li-3", 3, null, null, null, 3));

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), estimates, mapping, null, "EOC");

		// Then: identical to the no-mapping case — the exclusion path changes nothing when nothing is dropped
		assertThat(data.tactics().get(1).planSpend()).isEqualTo(100000.0);
		assertThat(data.tactics().get(2).planSpend()).isEqualTo(200000.0);
		assertThat(data.tactics().get(3).planSpend()).isEqualTo(300000.0);
		assertThat(data.tactics().get(3).planImps()).isEqualTo(900000.0);
	}

	@Test
	void collectShouldReadThePlansReachColumnPerTacticAndSkipExcludedRowsTest() {
		// Given: an Estimates tab with a Reach column per line item, and the middle line dropped at
		// matching time
		List<List<String>> sheet = List.of(
				List.of("Media"),
				List.of("programmatic display"),
				List.of("programmatic audio"),
				List.of("programmatic ctv")
		);
		List<List<String>> estimates = List.of(
				List.of("Media", "Total Cost", "Impressions", "Reach"),
				List.of("programmatic display", "100000", "500000", "120000"),
				List.of("programmatic audio", "200000", "700000", "300000"),
				List.of("programmatic ctv", "300000", "900000", "80000")
		);
		List<LineItemMapping> mapping = List.of(
				new LineItemMapping("Programmatic Display", "li-1", 1, null, null, null, 1),
				new LineItemMapping("Programmatic CTV", "li-3", 2, null, null, null, 3));

		// When:
		CampaignData data = collector.collect(sheet, List.of(), List.of(), estimates, mapping, null, "EOC");

		// Then: each reported tactic carries its own line's reach, and the dropped line contributes none
		assertThat(data.tactics().get(1).planReach()).isEqualTo(120000.0);
		assertThat(data.tactics().get(2).planReach()).isEqualTo(80000.0);
		assertThat(data.tactics()).containsOnlyKeys(1, 2);
	}
}
