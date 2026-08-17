package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.AudienceAgeRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.AudienceSegmentRow;
import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTakeawayInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.GeoRow;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherObservationInput;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.TacticNarrativeDigest;
import com.aidigital.reportconstructor.service.reports.dto.TacticThoughtsInput;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EomPromptBuilderTest {

	private final EomPromptBuilder builder = new EomPromptBuilder(new ClaudeResponseNormalizer(), new Fmt());

	private final ClaudeBatchPromptBuilder eoc =
			new ClaudeBatchPromptBuilder(new ClaudeResponseNormalizer(), new Fmt());

	private CampaignData campaign(Integer monthNumber, Integer monthsTotal) {
		Tactic ctv = new Tactic(
				"CTV", "CTV", null,
				5000.0, 1_000_000.0, 0.0, 1_000_000.0, null, 98.0, null, null,
				null, null, null, null, null, null, null, null);
		return new CampaignData(
				"Acme", "Spring Launch", "US", "Awareness", "Mar 1 - Mar 31",
				null, "$500,000", "Reach", "CTV", "25-44", "Auto intenders",
				new Totals(0, 0, 0, 0, null, null), Map.of(1, ctv), monthNumber, monthsTotal, null);
	}

	@Test
	void shouldTellEveryPerTacticCallTheCampaignIsStillRunningTest() {
		// When:
		String context = builder.campaignContextForConclusions(campaign(2, 6), "Drive awareness.");

		// Then: the framing the EOC builder never sends
		assertThat(context).contains("END-OF-MONTH report on a campaign that is STILL RUNNING");
		assertThat(context).contains("every plan figure is the month's target");
	}

	@Test
	void shouldKeepTheSharedCampaignContextTheEocBuilderBuildsTest() {
		// Given: the same run described by both builders
		CampaignData data = campaign(2, 6);

		// When:
		String eomContext = builder.campaignContextForConclusions(data, "Drive awareness.");

		// Then: the EOM header is additive — the plan/brief/results blocks are still the shared ones, so a
		// later change to the EOC context block still reaches EOM
		assertThat(eomContext).endsWith(eoc.campaignContextForConclusions(data, "Drive awareness."));
	}

	@Test
	void shouldNeverStateAMonthCadenceInTheSharedContextTest() {
		// Given: a run carrying the two EOM month fields, which are equal by construction — both are derived
		// from the selected reporting window, so a "month N of M" sentence would read "month 1 of 1"
		// When:
		String context = builder.campaignContextForConclusions(campaign(1, 1), "Drive awareness.");

		// Then: no cadence claim is repeated by every call of the run
		assertThat(context).contains("STILL RUNNING");
		assertThat(context).doesNotContain("reporting month 1 of");
		assertThat(context).doesNotContain("of the flight.");
	}

	private Pivot monthly(boolean clicks, boolean completions, double[]... months) {
		LinkedHashMap<String, double[]> data = new LinkedHashMap<>();
		String[] labels = {"Jan", "Feb", "Mar"};
		for (int i = 0; i < months.length; i++) {
			data.put(labels[i], months[i]);
		}
		return new Pivot(data, clicks, completions);
	}

	@Test
	void shouldAskTheStrategicNarrativeForMidFlightCopyNotAClosingVerdictTest() {
		// When:
		String prompt = builder.strategicNarrativePrompt(campaign(2, 6), "Drive awareness.", Map.of())
				.orElseThrow();

		// Then: the campaign is framed as running, and the overview is asked for as continued activity
		assertThat(prompt).contains("END-OF-MONTH campaign report");
		assertThat(prompt).contains("STILL RUNNING");
		assertThat(prompt).contains("we continued running");
		assertThat(prompt).contains("CAMPAIGN AS A WHOLE");
		assertThat(prompt).contains("THE CAMPAIGN HAS NOT ENDED");
		// and nothing asks for the closing-verdict tense the EOC prompt uses
		assertThat(prompt).doesNotContain("Past tense");
		assertThat(prompt).doesNotContain("past tense");
	}

	@Test
	void shouldKeepTheParsedContractAndCharacterBudgetsTheEocPromptUsesTest() {
		// When:
		String prompt = builder.strategicNarrativePrompt(campaign(2, 6), "Drive awareness.", Map.of())
				.orElseThrow();

		// Then: RealClaudeClient parses both flavours with the same code, so the keys, the field count and
		// the character budgets must not drift from the EOC prompt
		assertThat(prompt).contains("\"proposal_overview\": string");
		assertThat(prompt).contains("\"strategic_insights\": array");
		assertThat(prompt).contains("Exactly 4 objects: {\"point\": string, \"overview\": string}");
		assertThat(prompt).contains("MAX 20 CHARACTERS ABSOLUTE HARD LIMIT");
		assertThat(prompt).contains("MAX 230 CHARACTERS");
		assertThat(prompt).contains("Exactly 2 complete sentences");
		assertThat(prompt).contains("Return ONLY the JSON object");
	}

	@Test
	void shouldGiveTheModelTheMonthOverMonthSeriesToCompareAgainstTest() {
		// Given: one CTR tactic and one VCR tactic, each with two months on the reviewed sheet
		Map<Integer, Pivot> pivots = new LinkedHashMap<>();
		pivots.put(1, monthly(true, false, new double[] {1_000_000, 4_200, 0}, new double[] {1_500_000, 4_500, 0}));
		pivots.put(2, monthly(false, true, new double[] {500_000, 0, 480_000}, new double[] {600_000, 0, 588_000}));

		// When:
		String prompt = builder.strategicNarrativePrompt(campaign(2, 6), "Drive awareness.", pivots).orElseThrow();

		// Then: each tactic's months carry their own rate, and the campaign line sums only impressions —
		// a click rate and a completion rate averaged together would mean nothing
		assertThat(prompt).contains("=== MONTH-OVER-MONTH DELIVERY ===");
		assertThat(prompt).contains("Jan 1,000,000 imps, CTR 0.42%");
		assertThat(prompt).contains("Feb 1,500,000 imps, CTR 0.30%");
		assertThat(prompt).contains("Jan 500,000 imps, VCR 96.00%");
		assertThat(prompt).contains("Campaign total: Jan 1,500,000 imps | Feb 2,100,000 imps");
		assertThat(prompt).contains("COMPARE, DON'T RESTATE");
	}

	@Test
	void shouldOmitTheDeliveryTableWhenTheSheetCarriesNoPacingBlocksTest() {
		// Given: a sheet whose pacing blocks were never filled
		Map<Integer, Pivot> empty = Map.of(1, new Pivot(new LinkedHashMap<>(), false, false));

		// When:
		String prompt = builder.strategicNarrativePrompt(campaign(2, 6), "Drive awareness.", empty).orElseThrow();

		// Then: no empty table header is sent, and the prompt still stands on its own
		assertThat(prompt).doesNotContain("=== MONTH-OVER-MONTH DELIVERY ===");
		assertThat(prompt).contains("\"strategic_insights\": array");
	}

	@Test
	void shouldReuseTheSameCampaignContextBlockTheEocPromptBuildsTest() {
		// Given:
		CampaignData data = campaign(2, 6);

		// When:
		String prompt = builder.strategicNarrativePrompt(data, "Drive awareness.", Map.of()).orElseThrow();

		// Then: the brief/plan/tactic context is the shared one, so a later change to it reaches both flavours
		assertThat(prompt).endsWith(eoc.strategicNarrativeContext(data, "Drive awareness.").orElseThrow());
	}

	@Test
	void shouldLeaveTheEndOfCampaignPromptUntouchedByTheMonthlySeriesTest() {
		// Given: the monthly series only the EOM wording reads
		Map<Integer, Pivot> pivots = Map.of(
				1, monthly(true, false, new double[] {1_000_000, 4_200, 0}));
		CampaignData data = campaign(null, null);

		// When:
		String withSeries = eoc.strategicNarrativePrompt(data, "Drive awareness.", pivots).orElseThrow();

		// Then: the EOC seam ignores it — the prompt is exactly the one it has always sent
		assertThat(withSeries)
				.isEqualTo(eoc.buildBatchStrategicNarrativePrompt(data, "Drive awareness.").orElseThrow());
		assertThat(withSeries).doesNotContain("MONTH-OVER-MONTH");
	}

	@Test
	void shouldAskThePerTacticOverviewForPacingNotAClosingVerdictTest() {
		// When:
		String prompt = builder.buildTacticConclusionsPrompt(campaign(1, 1), List.of(1), "Drive awareness.")
				.orElseThrow();

		// Then: the role line, the extra principle and the overview spec all place the tactic mid-flight, and
		// the overview is asked for as pacing against the month's plan that an off-plan metric is argued for
		assertThat(prompt).contains("END-OF-MONTH report on a campaign that is STILL RUNNING");
		assertThat(prompt).doesNotContain("post-campaign report");
		assertThat(prompt).contains("8. MID-FLIGHT PACING, NOT A VERDICT.");
		assertThat(prompt).contains("Write ONLY about pacing");
		assertThat(prompt).contains("normal fluctuation of a live flight");
		assertThat(prompt).contains("never prescribe a fix, never write a closing verdict");
		assertThat(prompt).contains("with no DAILY PACING series for a tactic, say nothing about its day-by-day "
				+ "movement");
	}

	@Test
	void shouldGiveEachTacticItsDailyPacingCurveToArgueTheMonthFromTest() {
		// Given: the daily pacing block the user reviewed on the sheet, for tactic 1
		LinkedHashMap<String, double[]> days = new LinkedHashMap<>();
		days.put("Mar 1", new double[] {41_200, 120, 0});
		days.put("Mar 2", new double[] {38_900, 110, 0});
		days.put("Mar 3", new double[] {52_400, 160, 0});
		Map<Integer, Pivot> daily = Map.of(1, new Pivot(days, true, false));

		// When:
		String prompt = builder
				.tacticConclusionsPrompt(campaign(1, 1), List.of(1), "Drive awareness.", daily)
				.orElseThrow();

		// Then: impressions per day, oldest first, under the tactic's plan-vs-actual line
		assertThat(prompt).contains("DAILY PACING (impressions per day, oldest first): "
				+ "Mar 1 41,200 | Mar 2 38,900 | Mar 3 52,400");
		assertThat(prompt).contains("PERFORMANCE (for overview): ");
	}

	@Test
	void shouldSayNothingAboutDaysWhenTheSheetCarriesNoDailyBlockTest() {
		// Given: a tactic whose daily pacing block is absent from the reviewed sheet
		Map<Integer, Pivot> daily = Map.of(1, new Pivot(new LinkedHashMap<>(), false, false));

		// When:
		String prompt = builder
				.tacticConclusionsPrompt(campaign(1, 1), List.of(1), "Drive awareness.", daily)
				.orElseThrow();

		// Then: no empty pacing line the copy could read as a day with no delivery
		assertThat(prompt).doesNotContain("DAILY PACING (impressions");
	}

	@Test
	void shouldAskTheCampaignResultsCopyForPacingNotAFinishedCampaignTest() {
		// Given: the Step-4 call, reasoning over one tactic's digest
		CampaignData data = campaign(1, 1);
		List<TacticNarrativeDigest> digests = List.of(
				new TacticNarrativeDigest(1, "CTV", "CTV paced to 101% of the month's impression target.",
						List.of(), List.of()));

		// When:
		String prompt = builder
				.buildCampaignResultsPrompt(data, "Drive awareness.", null, digests).orElseThrow();

		// Then: the role line, the group overviews and the four thoughts all place the campaign mid-flight
		assertThat(prompt).contains("END-OF-MONTH report on a campaign that is STILL RUNNING");
		assertThat(prompt).doesNotContain("post-campaign report");
		assertThat(prompt).contains("how the group paced against the month's plan");
		assertThat(prompt).contains("no closing verdict — every tactic is still live");
		assertThat(prompt).contains("why the campaign is delivering the way it is");
		assertThat(prompt).doesNotContain("why the campaign succeeded");
	}

	@Test
	void shouldKeepTheInternalGroupNumberOutOfBothPassesOverviewsTest() {
		// Given: the Step-4 call and the Step-5 pass that rewrites what it produced
		CampaignData data = campaign(1, 1);
		List<TacticNarrativeDigest> digests = List.of(
				new TacticNarrativeDigest(1, "CTV", "CTV paced to 101% of the month's impression target.",
						List.of(), List.of()));
		ClaudeStrategic strategic = new ClaudeStrategic(
				null, null, "Throughout July we continued running CTV across auto intenders.", List.of());
		ClaudeResults results = new ClaudeResults(
				Map.of(1, "Group 1 paced to full budget in July."),
				List.of(), Map.of(), List.of(), null, null, null);

		// When:
		String batchC = builder
				.buildCampaignResultsPrompt(data, "Drive awareness.", null, digests).orElseThrow();
		String batchD = builder
				.alignPrompt(strategic, results, List.of(), "Drive awareness.", "Jul 1 - Jul 31, 2026")
				.orElseThrow();

		// Then: neither pass may open the slide's copy with the deck-layout label the client never sees
		for (String prompt : List.of(batchC, batchD)) {
			assertThat(prompt).contains(builder.groupNamingRule());
			assertThat(prompt).contains("NEVER name the group in the text — no 'Group 1'");
		}
	}

	@Test
	void shouldKeepTheInternalGroupNumberOutOfTheEndOfCampaignOverviewsTooTest() {
		// Given: an EOC run of the same two calls
		CampaignData data = campaign(null, null);
		List<TacticNarrativeDigest> digests = List.of(
				new TacticNarrativeDigest(1, "CTV", "CTV delivered 101% of its impression plan.",
						List.of(), List.of()));
		ClaudeStrategic strategic = new ClaudeStrategic(
				null, null, "We ran CTV across auto intenders.", List.of());
		ClaudeResults results = new ClaudeResults(
				Map.of(1, "CTV delivered 101% of its impression plan."),
				List.of(), Map.of(), List.of(), null, null, null);

		// When:
		String batchC = eoc.buildCampaignResultsPrompt(data, "Drive awareness.", null, digests).orElseThrow();
		String batchD = eoc.buildBatchDPrompt(strategic, results, List.of(), "Drive awareness.").orElseThrow();

		// Then: the label leaks the same way at end of campaign, so both passes carry the shared rule
		assertThat(batchC).contains(eoc.groupNamingRule());
		assertThat(batchD).contains(eoc.groupNamingRule());
	}

	@Test
	void shouldKeepTheCampaignResultsContractTheEocPromptUsesTest() {
		// Given: the same Step-4 call described by both builders
		CampaignData data = campaign(1, 1);
		List<TacticNarrativeDigest> digests = List.of(
				new TacticNarrativeDigest(1, "CTV", "CTV paced to 101% of the month's impression target.",
						List.of(), List.of()));

		// When:
		String prompt = builder
				.buildCampaignResultsPrompt(data, "Drive awareness.", null, digests).orElseThrow();

		// Then: the parsed keys, the group keying, the field caps and the naming rule are the parent's
		assertThat(prompt).contains("\"results_overviews\": { // Keyed by tactic-group number as strings (1)");
		assertThat(prompt).contains("EXACTLY 2 SENTENCES");
		assertThat(prompt).contains("\"thoughts_on_performance\": string, // EXACTLY 4 short paragraphs joined "
				+ "by \" | \" (exactly 3 separators)");
		assertThat(prompt).contains("\"optimization_recommendations\": array");
		assertThat(prompt).contains(eoc.tacticNamingRule());
	}

	@Test
	void shouldLeaveTheEndOfCampaignResultsPromptUntouchedTest() {
		// Given: an EOC run of the same Step-4 call
		CampaignData data = campaign(null, null);
		List<TacticNarrativeDigest> digests = List.of(
				new TacticNarrativeDigest(1, "CTV", "CTV delivered 101% of its impression plan.",
						List.of(), List.of()));

		// When:
		String prompt = eoc.buildCampaignResultsPrompt(data, "Drive awareness.", null, digests).orElseThrow();

		// Then: the frozen wording still goes out unchanged
		assertThat(prompt).startsWith("You are a senior digital media analyst writing the campaign-level copy "
				+ "for a post-campaign report.\n\n");
		assertThat(prompt).contains("EXACTLY 2 SENTENCES, past tense, ≤");
		assertThat(prompt).contains("(2) why the campaign succeeded — name the mechanism;");
		assertThat(prompt).doesNotContain("STILL RUNNING");
	}

	@Test
	void shouldAlignTheStoryAroundTheReportingMonthTest() {
		// Given: the Step-5 draft as the earlier calls left it, and the deck's flight-dates label
		ClaudeStrategic strategic = new ClaudeStrategic(
				null, null, "Throughout July we continued running CTV across auto intenders.",
				List.of(new StrategicInsight("Pacing held", "CTV held its delivery pace against July's plan.")));
		ClaudeResults results = new ClaudeResults(
				Map.of(1, "CTV paced to 101% of July's impression target."),
				List.of("CTV led the month's pacing."), Map.of(), List.of(), null, null, null);

		// When:
		String prompt = builder
				.alignPrompt(strategic, results, List.of(), "Drive awareness.", "Jul 1 - Jul 31, 2026")
				.orElseThrow();

		// Then: the pass is told it is editing a live campaign, and the month is named once for every field
		assertThat(prompt).contains("END-OF-MONTH report for a campaign that is STILL RUNNING");
		assertThat(prompt).contains("story of THIS REPORTING MONTH");
		assertThat(prompt).contains("=== REPORTING PERIOD ===\nThis report covers Jul 1 - Jul 31, 2026");
		assertThat(prompt).contains("never align a field into a closing verdict");
		assertThat(prompt).contains("about how that group paced this month — no closing verdict");
		// and rules 2-4 are the parent's, so a later edit to them reaches both flavours
		assertThat(prompt).contains(eoc.alignSharedRules());
	}

	@Test
	void shouldSayNothingAboutThePeriodWhenTheDeckCarriesNoFlightLabelTest() {
		// Given: a run whose sheet left the flight dates blank
		ClaudeStrategic strategic = new ClaudeStrategic(
				null, null, "We continued running CTV across auto intenders.", List.of());
		ClaudeResults results = new ClaudeResults(
				Map.of(1, "CTV paced to 101% of the month's impression target."),
				List.of(), Map.of(), List.of(), null, null, null);

		// When:
		String prompt = builder.alignPrompt(strategic, results, List.of(), "Drive awareness.", "  ").orElseThrow();

		// Then: no empty period claim the copy could align itself to
		assertThat(prompt).doesNotContain("=== REPORTING PERIOD ===");
		assertThat(prompt).contains("STILL RUNNING");
	}

	@Test
	void shouldLeaveTheEndOfCampaignAlignmentPromptUntouchedTest() {
		// Given: an EOC run of the same pass, handed a period label the EOC wording ignores
		ClaudeStrategic strategic = new ClaudeStrategic(
				null, null, "We ran CTV across auto intenders.", List.of());
		ClaudeResults results = new ClaudeResults(
				Map.of(1, "CTV delivered 101% of its impression plan."),
				List.of(), Map.of(), List.of(), null, null, null);

		// When:
		String prompt = eoc.alignPrompt(strategic, results, List.of(), "Drive awareness.", "Jul 1 - Jul 31, 2026")
				.orElseThrow();

		// Then: the prompt is exactly the one it has always sent
		assertThat(prompt).isEqualTo(
				eoc.buildBatchDPrompt(strategic, results, List.of(), "Drive awareness.").orElseThrow());
		assertThat(prompt).doesNotContain("REPORTING PERIOD");
		assertThat(prompt).doesNotContain("STILL RUNNING");
		assertThat(prompt).contains("Exactly 2 sentences, past tense, no line breaks.");
	}

	@Test
	void shouldTellEveryBreakdownSectionItIsWritingAMonthlySlideTest() {
		// Given: one tactic's filled table per section
		CampaignData data = campaign(1, 1);
		AudienceTable audienceTable = new AudienceTable("25-34", "58% F / 42% M",
				List.of(new AudienceAgeRow("25-34", "1,200,000")),
				List.of(new AudienceSegmentRow("Auto Intenders", "142")));
		CreativeTable creativeTable = new CreativeTable("6", "1.85%", "0.42%", "Spot 30s",
				List.of(new CreativeRow("Spot 30s", "400,000", "0.12%", "94.1%", "$2,400")));
		GeoTable geoTable = new GeoTable("14", "New York", "97.1%",
				List.of(new GeoRow("New York", "400,000", "97.1%")));
		DeviceTable deviceTable = new DeviceTable("0.42%", "97.1%", "4", "CTV", "62%",
				List.of(new DeviceRow("CTV", "400,000", "", "97.1%", "$2,400")));

		// When:
		String audience = builder
				.buildAudienceSectionPrompt(new AudienceInsightInput(1, "CTV", audienceTable), data, "Go.", 200, 120)
				.orElseThrow();
		String publisher = builder.buildPublisherSectionPrompt(
				new PublisherObservationInput(1, "CTV", List.of(new PublisherRow("Hulu", "400,000", "40%")),
						400_000L, 1_000_000L),
				data, "Go.", 120).orElseThrow();
		String creative = builder
				.buildCreativeSectionPrompt(new CreativeTakeawayInput(1, "CTV", "VCR", creativeTable), data, "Go.",
						120, 140)
				.orElseThrow();
		String geo = builder
				.buildGeoSectionPrompt(new GeoInsightInput(1, "CTV", "VCR", geoTable), data, "Go.", 120)
				.orElseThrow();
		String device = builder
				.buildDeviceSectionPrompt(new DeviceInsightInput(1, "CTV", deviceTable), data, "Go.", 200, 120)
				.orElseThrow();

		// Then: every section says which kind of report it is writing, and none of them still says post-campaign
		for (String prompt : List.of(audience, publisher, creative, geo, device)) {
			assertThat(prompt).contains("an END-OF-MONTH report on a campaign that is STILL RUNNING, covering one "
					+ "reporting month of a longer flight");
			assertThat(prompt).doesNotContain("post-campaign report");
			// and the shared context tells all five what the figures underneath them are
			assertThat(prompt).contains("every plan figure is the month's target");
			// while everything the section actually asks for is the parent's, unchanged
			assertThat(prompt).contains(eoc.sectionPrinciples());
		}
		assertThat(audience).contains(eoc.sectionObjectRules(4));
		assertThat(geo).contains(eoc.sectionObjectRules(4));
	}

	@Test
	void shouldLeaveTheEndOfCampaignSectionPromptsUntouchedTest() {
		// Given: an EOC run of the same audience section
		CampaignData data = campaign(null, null);
		AudienceTable audienceTable = new AudienceTable("25-34", "58% F / 42% M",
				List.of(new AudienceAgeRow("25-34", "1,200,000")),
				List.of(new AudienceSegmentRow("Auto Intenders", "142")));

		// When:
		String prompt = eoc
				.buildAudienceSectionPrompt(new AudienceInsightInput(1, "CTV", audienceTable), data, "Go.", 200, 120)
				.orElseThrow();

		// Then: the frozen wording still goes out unchanged
		assertThat(prompt).startsWith("You are a senior digital media analyst writing the 'Audience analysis' slide "
				+ "for ONE tactic in a post-campaign report.\n\n");
		assertThat(prompt).doesNotContain("STILL RUNNING");
	}

	@Test
	void shouldAskTheTacticThoughtsForPacingNotAHeadlineResultTest() {
		// Given: the Step-3 call for one tactic that ran a publisher breakdown
		TacticThoughtsInput input = new TacticThoughtsInput(
				1, "CTV", "CTV paced to 101% of the month's impression target.",
				List.of("Hulu carried the largest share."), List.of(), List.of(), List.of(), List.of());

		// When:
		String prompt = builder.buildTacticThoughtsPrompt(input, "Drive awareness.", 200, 470).orElseThrow();

		// Then: the role line frames a live campaign, and only the first angle changes to pacing
		assertThat(prompt).contains("END-OF-MONTH report on a campaign that is STILL RUNNING");
		assertThat(prompt).doesNotContain("end-of-campaign report");
		assertThat(prompt).contains("how the tactic is pacing this month and WHY");
		assertThat(prompt).doesNotContain("the tactic's headline result and WHY");
		// and the other three angles, the four-thought count and the JSON shape are the parent's
		assertThat(prompt).contains("what worked best across its breakdowns");
		assertThat(prompt).contains("(3) a watch-out or nuance; (4) the forward-looking opportunity");
		assertThat(prompt).contains("Write EXACTLY 4 short analytical thoughts");
		assertThat(prompt).contains("{\"thoughts\": [\"...\", \"...\", \"...\", \"...\"], \"story\": \"...\"}");
		// and the closing story spec is inherited from the parent, unchanged for a live campaign
		assertThat(prompt).contains("Then write ONE closing narrative");
	}

	@Test
	void shouldLeaveTheEndOfCampaignTacticThoughtsPromptUntouchedTest() {
		// Given: an EOC run of the same call
		TacticThoughtsInput input = new TacticThoughtsInput(
				1, "CTV", "CTV delivered 101% of its impression plan.",
				List.of("Hulu carried the largest share."), List.of(), List.of(), List.of(), List.of());

		// When:
		String prompt = eoc.buildTacticThoughtsPrompt(input, "Drive awareness.", 200, 470).orElseThrow();

		// Then: the frozen wording still goes out unchanged
		assertThat(prompt).startsWith("You are a senior digital media analyst writing the 'Thoughts on tactic "
				+ "performance' slide for ONE tactic in an end-of-campaign report");
		assertThat(prompt).contains("(1) the tactic's headline result and WHY");
		assertThat(prompt).doesNotContain("STILL RUNNING");
	}

	@Test
	void shouldFrameTheClassicBatchAPromptAsMidFlightTest() {
		// Given: the classic direct-to-deck flow's full Batch A call (audience + narrative combined) — the
		// only other place, besides the sheet-as-source flow, that ever asks for proposal/insights copy
		CampaignData data = campaign(1, 1);

		// When:
		String prompt = builder.buildBatchAPrompt(data, "Drive awareness.").orElseThrow();

		// Then: this call carries no shared EOM context block of its own, so the proposal/insights specs are
		// the only place it learns the campaign has not ended
		assertThat(prompt).contains("STILL RUNNING");
		assertThat(prompt).contains("we continued running");
		assertThat(prompt).doesNotContain("why the campaign ran");
		assertThat(prompt).doesNotContain("how it ran");
		// and it does not ask for a month-over-month movement it has no table to support
		assertThat(prompt).doesNotContain("against the previous months");
		assertThat(prompt).contains("a notable pattern in THIS reporting month's delivery");
		// while the audience fields, the JSON shape and the character budgets are the parent's, unchanged
		assertThat(prompt).contains("\"audience_age\": string");
		assertThat(prompt).contains("\"audience_segments\": string");
		assertThat(prompt).contains("MAX 20 CHARACTERS ABSOLUTE HARD LIMIT");
		assertThat(prompt).contains("MAX 230 CHARACTERS");
	}

	@Test
	void shouldLeaveTheEndOfCampaignBatchAPromptUntouchedTest() {
		// Given: an EOC run of the same classic call
		CampaignData data = campaign(null, null);

		// When:
		String prompt = eoc.buildBatchAPrompt(data, "Drive awareness.").orElseThrow();

		// Then: the frozen wording still goes out unchanged
		assertThat(prompt).contains("why the campaign ran — client objective + target audience");
		assertThat(prompt).contains("how it ran — tactic mix + geo + flight period");
		assertThat(prompt).doesNotContain("STILL RUNNING");
	}

	@Test
	void shouldLeaveTheEndOfCampaignConclusionsBlindToTheDailyCurveTest() {
		// Given: the same daily series handed to the EOC seam
		LinkedHashMap<String, double[]> days = new LinkedHashMap<>();
		days.put("Mar 1", new double[] {41_200, 120, 0});
		Map<Integer, Pivot> daily = Map.of(1, new Pivot(days, true, false));
		CampaignData data = campaign(null, null);

		// When:
		String prompt = eoc.tacticConclusionsPrompt(data, List.of(1), "Drive awareness.", daily).orElseThrow();

		// Then: the EOC prompt is exactly the one it has always sent
		assertThat(prompt).isEqualTo(eoc.buildTacticConclusionsPrompt(data, List.of(1), "Drive awareness.")
				.orElseThrow());
		assertThat(prompt).doesNotContain("DAILY PACING");
	}

	@Test
	void shouldKeepTheConclusionsContractAndSharedRulesTheEocPromptUsesTest() {
		// Given: the same chunk of tactics described by both builders
		CampaignData data = campaign(1, 1);

		// When:
		String prompt = builder.buildTacticConclusionsPrompt(data, List.of(1), "Drive awareness.").orElseThrow();

		// Then: one keyed "overview" field, the same 190-character budget and the parent's principles
		assertThat(prompt).contains("carrying exactly ONE field: \"overview\"");
		assertThat(prompt).contains("{\"tactic_1\": {\"overview\": \"...\"}, \"tactic_2\": {\"overview\": \"...\"}}");
		assertThat(prompt).contains("MAX 190 CHARACTERS");
		assertThat(prompt).contains(eoc.overviewFocusMetrics());
		assertThat(prompt).contains(eoc.conclusionPrincipleList());
		assertThat(prompt).contains(eoc.conclusionsOutputRules());
	}

	@Test
	void shouldLeaveTheEndOfCampaignConclusionsPromptUntouchedTest() {
		// Given: the seams the EOM builder overrides are the EOC ones for an EOC run
		CampaignData data = campaign(null, null);

		// When:
		String prompt = eoc.buildTacticConclusionsPrompt(data, List.of(1), "Drive awareness.").orElseThrow();

		// Then: the frozen wording still goes out unchanged
		assertThat(prompt).startsWith("You are a senior digital media analyst writing per-tactic conclusions "
				+ "for a post-campaign report.\n\n");
		assertThat(prompt).contains("[what the tactic delivered vs plan] + [WHY it performed as it did] "
				+ "+ [business so-what]. Past tense, business English, max 2 sentences, no bullets. "
				+ "Focus metrics by tactic type:");
		assertThat(prompt).doesNotContain("MID-FLIGHT");
		assertThat(prompt).doesNotContain("STILL RUNNING");
	}
}
