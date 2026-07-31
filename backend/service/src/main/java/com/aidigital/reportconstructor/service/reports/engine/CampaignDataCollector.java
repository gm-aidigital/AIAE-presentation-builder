package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.RateType;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.dto.WindowMetrics;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single-pass aggregation of a campaign from its BigQuery export.
 *
 * <p>Single pass over the BigQuery export ({@code adjRows}) to compute campaign
 * totals, per-channel and per-line-item aggregates, weekday/weekend split and the
 * top delivery creative; plus a parse of the Estimates tab for planned KPIs. The
 * result is consumed by the resolvers and Claude batches.
 */
@Slf4j
@Component
public class CampaignDataCollector {

	private final SheetRowHelper sheetUtils;
	private final TacticExtractionHelper tacticExtraction;
	private final CampaignResolvers campaignResolvers;
	private final RatePlanCalculator ratePlanCalculator;

	/**
	 * Wires the collaborators used to scan the raw grids and resolve plan figures.
	 *
	 * @param sheetUtils         label/value lookups against Media Plan / Adjustments grids
	 * @param tacticExtraction   tactic-name extraction and channel/KPI-type lookups
	 * @param campaignResolvers  shared resolver used to build the tactics-list summary
	 * @param ratePlanCalculator EOM rate/budget-to-Plan-Units math
	 */
	public CampaignDataCollector(
			SheetRowHelper sheetUtils, TacticExtractionHelper tacticExtraction, CampaignResolvers campaignResolvers,
			RatePlanCalculator ratePlanCalculator) {
		this.sheetUtils = sheetUtils;
		this.tacticExtraction = tacticExtraction;
		this.campaignResolvers = campaignResolvers;
		this.ratePlanCalculator = ratePlanCalculator;
	}

	private static final String[] STOP_WORDS = {"added value", "totals", "please note", "total:"};

	/** Max tactics the report template carries — the per-tactic scan is bounded to this. */
	private static final int MAX_TACTICS = 28;

	/**
	 * Mutable per-key accumulator (channel or line-item).
	 */
	private static final class Agg {

		double spend;
		double imps;
		double clicks;
		double completions;
		double weekdayImps;
		double weekendImps;
		boolean hasCompletions;
	}

	/**
	 * Collects campaign totals and per-tactic metrics from the raw grids.
	 *
	 * @param sheetRows       Media Plan grid rows
	 * @param adjRows         raw delivery/Adjustments grid rows
	 * @param audienceRows    audience-breakdown grid rows
	 * @param estimatesRows   Estimates tab grid rows
	 * @param lineItemMapping tactic-to-line-item mapping; for EOM this also carries each tactic's
	 *                        rate/budget economics entered at matching time
	 * @param dateFilter      user-confirmed Flight dates window entered at Data Inputs (the flight window;
	 *                        for EOM this is also the sole source of the full-flight length — how many
	 *                        calendar months it spans becomes both {@code eom_month_number} and
	 *                        {@code eom_flight_months_total}, since this report always covers the whole
	 *                        period its plan figures are measured against)
	 * @param reportType      report template code; {@code "EOM"} resolves plan figures from
	 *                        {@code lineItemMapping}'s rate/budget fields (plus CTR/VCR/max-frequency
	 *                        benchmarks still read from the Estimates tab), anything else keeps resolving
	 *                        every plan figure from the Estimates tab.
	 * @return the aggregated campaign data
	 */
	public CampaignData collect(
			List<List<String>> sheetRows,
			List<List<String>> adjRows,
			List<List<String>> audienceRows,
			List<List<String>> estimatesRows,
			List<LineItemMapping> lineItemMapping,
			DateFilter dateFilter,
			String reportType
	) {
		if (sheetRows == null) {
			sheetRows = List.of();
		}
		if (adjRows == null) {
			adjRows = List.of();
		}
		if (audienceRows == null) {
			audienceRows = List.of();
		}
		if (estimatesRows == null) {
			estimatesRows = List.of();
		}
		if (lineItemMapping == null) {
			lineItemMapping = List.of();
		}

		// ── 1. Campaign fields: adj overrides sheet ───────────────────────────
		String client = coalesce(sheetUtils.findLabelValue(adjRows, "Client name:"),
				sheetUtils.findLabelValue(sheetRows, "Client name:"));
		String campaign = coalesce(sheetUtils.findLabelValue(adjRows, "Campaign:"),
				sheetUtils.findLabelValue(sheetRows, "Campaign:"));
		String geo = coalesce(sheetUtils.findLabelValue(adjRows, "Geo locations:"),
				coalesce(sheetUtils.findLabelValue(sheetRows, "Geo locations:"),
						joinColumn(sheetRows, MediaPlanColumn.GEO)));
		String goal = coalesce(sheetUtils.findLabelValue(adjRows, "Funnel stages:"),
				coalesce(sheetUtils.findLabelValue(sheetRows, "Funnel stages:"),
						joinColumn(sheetRows, MediaPlanColumn.FUNNEL)));
		String budget = coalesce(sheetUtils.findLabelValue(adjRows, "Total investment:"),
				sheetUtils.findLabelValue(sheetRows, "Total investment:"));
		String kpis = coalesce(sheetUtils.findLabelValue(adjRows, "Primary KPIs:"),
				sheetUtils.findLabelValue(sheetRows, "Primary KPIs:"));

		// ── 2. Flight window: user-confirmed date filter over the raw data ────
		FlightDates flightTs = resolveDateWindow(dateFilter, adjRows);
		String flightDates = flightTs != null ? sheetUtils.formatFlightDates(flightTs.start(), flightTs.end()) : null;

		// ── 3. Tactics list ───────────────────────────────────────────────────
		String tacticsList = campaignResolvers.resolveTacticsList(sheetRows, adjRows).value();

		// ── 4. Explicit audience fields ───────────────────────────────────────
		String audienceAge = coalesce(sheetUtils.findLabelValue(adjRows, "Audience age:"),
				sheetUtils.findLabelValue(sheetRows, "Audience age:"));
		String audienceSegs = coalesce(sheetUtils.findLabelValue(adjRows, "Audience segments:"),
				sheetUtils.findLabelValue(sheetRows, "Audience segments:"));

		// ── 5. Estimates tab → planned KPIs by tactic ─────────────────────────
		Map<String, Deque<double[]>> estimatesByTactic = parseEstimates(estimatesRows);
		// double[] layout: {spend, imps, ctr, vcr, maxFreq}; NaN = null. Keyed by tactic name to a FIFO queue,
		// because a media plan repeats a channel name across several line items (e.g. "Meta" four times) with
		// different plan figures each; the queue keeps every line item's own numbers in media-plan order.

		// ── 6. Tactics & channel mapping ──────────────────────────────────────
		List<String> mediaTactics = tacticExtraction.extractTacticsFromMedia(sheetRows);
		Map<Integer, String[]> tacticMap = new LinkedHashMap<>(); // N -> [name, channel|null]
		for (int n = 1; n <= MAX_TACTICS; n++) {
			String name = coalesce(sheetUtils.findLabelValue(adjRows, "Tactic " + n + ":"),
					coalesce(sheetUtils.findLabelValue(sheetRows, "Tactic " + n + ":"),
							n - 1 < mediaTactics.size() ? mediaTactics.get(n - 1) : null));
			if (name == null) {
				continue;
			}
			tacticMap.put(n, new String[]{name, tacticExtraction.getTacticChannelFilter(name)});
		}

		// Join line items to tactics by tactic_num carried in the mapping payload.
		// The tactic NAME is never used for the join:
		// an Adjustments/sheet "Tactic N:" override renames the tactic but must not
		// break the line-item match. liToTacticNum gates row aggregation (presence of
		// the id in the mapping); numToLiId resolves the id for each tactic position.
		Map<String, Integer> liToTacticNum = new LinkedHashMap<>();
		Map<Integer, String> numToLiId = new LinkedHashMap<>();
		for (LineItemMapping m : lineItemMapping) {
			String id = m.lineItemId() == null ? "" : m.lineItemId().trim();
			if (id.isEmpty()) {
				continue;
			}
			int num = m.tacticNum() == null ? 0 : m.tacticNum();
			liToTacticNum.put(id, num);
			if (num > 0) {
				numToLiId.putIfAbsent(num, id);
			}
		}

		// ── 6b. Column detection (window-independent, done once) ──────────────
		int hIdx = -1;
		int colDt = -1;
		int colCh = -1;
		int colCo = -1;
		int colIm = -1;
		int colCl = -1;
		int colCmp = -1;
		int colDow = -1;
		int colLi = -1;
		int colCr = -1;
		for (int i = 0; i < adjRows.size(); i++) {
			List<String> row = adjRows.get(i);
			if (row == null) {
				continue;
			}
			Map<String, Integer> f = new LinkedHashMap<>();
			for (int j = 0; j < row.size(); j++) {
				String v = cell(row, j).toLowerCase(Locale.ROOT);
				switch (v) {
					case "date" -> f.put("date", j);
					case "channel" -> f.put("channel", j);
					case "cost" -> f.put("cost", j);
					case "impressions" -> f.put("imps", j);
					case "clicks" -> f.put("clicks", j);
					case "completions" -> f.put("completions", j);
					default -> {
					}
				}
				if (v.equals("day_of_week") || v.equals("dayofweek") || v.equals("day")) {
					f.put("dow", j);
				}
				if (v.equals("line item id") || v.equals("line_item_id") || v.equals("lineitemid") || v.equals("line" +
						" " +
						"item")) {
					f.put("li", j);
				}
				if (v.equals("creative") || v.equals("creative name") || v.equals("creative_name")) {
					f.put("creative", j);
				}
			}
			if (f.containsKey("date") && f.containsKey("channel") && f.containsKey("cost") && f.containsKey("imps")) {
				hIdx = i;
				colDt = f.get("date");
				colCh = f.get("channel");
				colCo = f.get("cost");
				colIm = f.get("imps");
				colCl = f.getOrDefault("clicks", -1);
				colCmp = f.getOrDefault("completions", -1);
				colDow = f.getOrDefault("dow", -1);
				colLi = f.getOrDefault("li", -1);
				colCr = f.getOrDefault("creative", -1);
				break;
			}
		}

		int colL1Naming = -1;
		if (colLi < 0 && hIdx >= 0) {
			List<String> hdr = adjRows.get(hIdx);
			for (int j = 0; j < hdr.size(); j++) {
				if (cell(hdr, j).toLowerCase(Locale.ROOT).contains("level 1 naming")) {
					colL1Naming = j;
					break;
				}
			}
		}

		// ── 6c/7/8: aggregate delivery rows over the flight window ─────────────
		boolean isEom = "EOM".equals(reportType);
		// EOM-only: months the report covers, from the same user-entered Flight dates (Data Inputs)
		// that also bound the actuals window — the full-flight target the user's monthly budget
		// multiplies by. No separate elapsed-vs-total distinction: the report always covers the whole
		// period being multiplied against, so this figure doubles as eomMonthNumber below.
		Integer eomMonthNumber = isEom && flightTs != null
				? ratePlanCalculator.monthsSpanned(flightTs.start(), flightTs.end()) : null;
		Integer eomFlightMonthsTotal = eomMonthNumber;
		Map<Integer, double[]> estimatesPlan = resolvePlanByTacticNum(tacticMap, estimatesByTactic);
		Map<Integer, double[]> planByTacticNum = isEom
				? resolveEomPlanByTacticNum(lineItemMapping, eomFlightMonthsTotal, estimatesPlan)
				: estimatesPlan;

		LocalDate flightStart = flightTs != null ? flightTs.start() : null;
		LocalDate flightEnd = flightTs != null ? flightTs.end() : null;
		WindowMetrics flightMetrics = aggregateWindow(adjRows, hIdx, colDt, colCh, colCo, colIm, colCl, colCmp,
				colDow, colLi, colCr, colL1Naming, liToTacticNum, tacticMap, numToLiId, planByTacticNum,
				flightStart, flightEnd);
		Totals totals = flightMetrics.totals();
		Map<Integer, Tactic> tacticsData = flightMetrics.tactics();

		// ── 9. Audience tab text (Batch A) ────────────────────────────────────
		List<String> audLines = new ArrayList<>();
		int aLimit = Math.min(200, audienceRows.size());
		for (int i = 0; i < aLimit; i++) {
			List<String> row = audienceRows.get(i);
			if (row == null) {
				continue;
			}
			List<String> cells = new ArrayList<>();
			for (String c : row) {
				String t = c == null ? "" : c.trim();
				if (!t.isEmpty()) {
					cells.add(t);
				}
			}
			if (!cells.isEmpty()) {
				audLines.add(String.join(" | ", cells));
			}
		}
		String audienceTabText = String.join("\n", audLines);

		return new CampaignData(
				client, campaign, geo, goal, flightDates, flightTs, budget, kpis, tacticsList,
				audienceAge, audienceSegs,
				totals,
				tacticsData,
				eomMonthNumber,
				isEom ? eomFlightMonthsTotal : null,
				audienceTabText
		);
	}

	/**
	 * Resolves each tactic's planned Estimates-tab row once, draining the FIFO queues built by
	 * {@link #parseEstimates}.
	 *
	 * @param tacticMap         tactic number to {@code [name, channel]} mapping
	 * @param estimatesByTactic Estimates-tab rows queued by lowercased tactic name
	 * @return tactic number to its planned {@code {spend, imps, ctr, vcr, maxFreq}} row, omitting tactics
	 * with no matching Estimates row
	 */
	Map<Integer, double[]> resolvePlanByTacticNum(Map<Integer, String[]> tacticMap,
	                                              Map<String, Deque<double[]>> estimatesByTactic) {
		Map<Integer, double[]> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, String[]> e : tacticMap.entrySet()) {
			String name = e.getValue()[0];
			Deque<double[]> queue = estimatesByTactic.get(name.trim().toLowerCase(Locale.ROOT));
			double[] plan = queue == null ? null : queue.poll();
			if (plan != null) {
				out.put(e.getKey(), plan);
			}
		}
		return out;
	}

	/**
	 * Resolves each EOM tactic's full-flight planned figures from the rate/budget economics entered by
	 * the user at matching time (joined by {@code tacticNum}, never by name): the monthly budget times
	 * the user-entered flight-months total gives the full-flight spend target, converted to Plan
	 * Units by rate type. The per-tactic {@code plan ctd} / {@code proj} pacing resolvers prorate this
	 * full-flight figure down to the elapsed months themselves (see {@link TacticResolvers}) — this
	 * method resolves the un-prorated, whole-flight target only, mirroring the role
	 * {@link #resolvePlanByTacticNum} plays for EOC. CTR/VCR/max-frequency benchmarks have no rate/budget
	 * equivalent, so they still come from {@code estimatesPlanByTacticNum} (the Estimates tab) even
	 * for EOM.
	 *
	 * @param lineItemMapping         tactic-to-line-item mapping carrying each tactic's
	 *                                rateType/unitPrice/monthlyBudget
	 * @param eomFlightMonthsTotal    total months the flight spans, derived from the Flight dates entered
	 *                                at Data Inputs; {@code null} or non-positive yields no plan figures at all
	 * @param estimatesPlanByTacticNum tactic number to its Estimates-tab row, used only for the
	 *                                CTR/VCR/max-frequency benchmarks (indices 2-4)
	 * @return tactic number to its planned {@code {spend, imps, ctr, vcr, maxFreq, clicks, views}} row.
	 * {@code imps} is populated when derivable — directly for a CPM tactic, or backed out of the planned
	 * clicks/completions and the Estimates CTR/VCR benchmark for CPC/CPV — for the reach/CPM/projection
	 * math that needs true impressions regardless of how the tactic was bought (see
	 * {@link TacticResolvers#resolveTacticReach}); the sheet's "Unit Plan" column instead shows
	 * {@code clicks}/{@code views} directly for a CPC/CPV tactic (see
	 * {@link TacticResolvers#resolveTacticImpsPlan}). Omits tactics with no mapping entry.
	 */
	Map<Integer, double[]> resolveEomPlanByTacticNum(
			List<LineItemMapping> lineItemMapping, Integer eomFlightMonthsTotal,
			Map<Integer, double[]> estimatesPlanByTacticNum) {
		Map<Integer, double[]> out = new LinkedHashMap<>();
		if (eomFlightMonthsTotal == null || eomFlightMonthsTotal <= 0) {
			return out;
		}
		for (LineItemMapping m : lineItemMapping) {
			Integer num = m.tacticNum();
			if (num == null || num <= 0) {
				continue;
			}
			Double fullBudget = m.monthlyBudget() == null ? null : m.monthlyBudget() * eomFlightMonthsTotal;
			Double units = ratePlanCalculator.planUnits(fullBudget, m.unitPrice(), m.rateType());
			double spend = fullBudget == null ? Double.NaN : fullBudget;
			double clicks = units != null && m.rateType() == RateType.CPC ? units : Double.NaN;
			double views = units != null && m.rateType() == RateType.CPV ? units : Double.NaN;
			double[] estimates = estimatesPlanByTacticNum.get(num);
			double ctr = estimates != null ? estimates[2] : Double.NaN;
			double vcr = estimates != null ? estimates[3] : Double.NaN;
			double maxFreq = estimates != null ? estimates[4] : Double.NaN;
			double imps;
			if (units != null && m.rateType() == RateType.CPM) {
				imps = units;
			} else if (!Double.isNaN(clicks) && !Double.isNaN(ctr) && ctr > 0) {
				imps = clicks / (ctr / 100);
			} else if (!Double.isNaN(views) && !Double.isNaN(vcr) && vcr > 0) {
				imps = views / (vcr / 100);
			} else {
				imps = Double.NaN;
			}
			log.info("[eom-plan] tacticNum={} rateType={} unitPrice={} monthlyBudget={} "
							+ "flightMonthsTotal={} fullBudget={} units={} imps={}",
					num, m.rateType(), m.unitPrice(), m.monthlyBudget(), eomFlightMonthsTotal, fullBudget, units, imps);
			out.putIfAbsent(num, new double[]{spend, imps, ctr, vcr, maxFreq, clicks, views});
		}
		return out;
	}

	/**
	 * Aggregates the flight window's delivery rows into campaign totals and per-tactic metrics, reusing
	 * the column layout, tactic/line-item wiring and plan figures resolved once by the caller.
	 *
	 * @param adjRows         raw delivery rows
	 * @param hIdx            header row index (-1 when no delivery header was found)
	 * @param colDt           date column index
	 * @param colCh           channel column index
	 * @param colCo           cost column index
	 * @param colIm           impressions column index
	 * @param colCl           clicks column index (-1 when absent)
	 * @param colCmp          completions column index (-1 when absent)
	 * @param colDow          day-of-week column index (-1 when absent)
	 * @param colLi           line-item id column index (-1 when absent)
	 * @param colCr           creative column index (-1 when absent)
	 * @param colL1Naming     "Level 1 Naming" fallback column index, used only when {@code colLi < 0}
	 * @param liToTacticNum   line-item id to tactic-number mapping, gating which rows aggregate by line item
	 * @param tacticMap       tactic number to {@code [name, channel]} mapping
	 * @param numToLiId       tactic number to its line-item id
	 * @param planByTacticNum tactic number to its window-independent planned figures
	 * @param windowStart     first day to include (inclusive), or {@code null} to include every row
	 * @param windowEnd       last day to include (inclusive); required when {@code windowStart} is non-null
	 * @return the window's campaign totals and per-tactic metrics
	 */
	WindowMetrics aggregateWindow(
			List<List<String>> adjRows,
			int hIdx, int colDt, int colCh, int colCo, int colIm, int colCl, int colCmp, int colDow, int colLi,
			int colCr, int colL1Naming,
			Map<String, Integer> liToTacticNum, Map<Integer, String[]> tacticMap, Map<Integer, String> numToLiId,
			Map<Integer, double[]> planByTacticNum,
			LocalDate windowStart, LocalDate windowEnd
	) {
		Agg totals = new Agg();
		double[] impsWithCompletions = {0.0};
		Map<String, Agg> byChannel = new LinkedHashMap<>();
		Map<String, Agg> byLineItemId = new LinkedHashMap<>();
		Map<String, Map<String, double[]>> byCreative = new LinkedHashMap<>(); // liId -> creative -> {imps, clicks}

		if (hIdx >= 0) {
			for (int i = hIdx + 1; i < adjRows.size(); i++) {
				List<String> row = adjRows.get(i);
				if (row == null) {
					continue;
				}

				String dateVal = cellAt(row, colDt);
				if (dateVal.isEmpty()) {
					continue;
				}
				LocalDate ts = sheetUtils.parseDate(dateVal);
				if (ts == null) {
					continue;
				}
				if (windowStart != null && (ts.isBefore(windowStart) || ts.isAfter(windowEnd))) {
					continue;
				}

				String chVal = cellAt(row, colCh).toLowerCase(Locale.ROOT);

				String liId = null;
				if (colLi >= 0) {
					String v = cellAt(row, colLi);
					if (!v.isEmpty()) {
						liId = v;
					}
				} else if (colL1Naming >= 0) {
					String naming = cellAt(row, colL1Naming);
					if (!naming.isEmpty()) {
						String[] parts = naming.split("_", -1);
						String candidate = parts.length > 8 ? parts[8].trim() : "";
						if (!candidate.isEmpty() && !candidate.equals("-") && candidate.chars().allMatch(Character::isDigit)) {
							liId = candidate;
						}
					}
				}

				double co = toFloat(cleanNum(cellAt(row, colCo), true));
				double im = toFloat(cleanNum(cellAt(row, colIm), false));
				double cl = colCl >= 0 ? toFloat(cleanNum(cellAt(row, colCl), false)) : 0.0;
				double cmp = colCmp >= 0 ? toFloat(cleanNum(cellAt(row, colCmp), false)) : 0.0;

				boolean isWeekend;
				if (colDow >= 0) {
					String dowVal = cellAt(row, colDow).toLowerCase(Locale.ROOT);
					if (isNumeric(dowVal)) {
						int d = (int) toFloat(dowVal);
						isWeekend = d == 0 || d == 6 || d == 7;
					} else {
						isWeekend =
								dowVal.equals("saturday") || dowVal.equals("sunday") || dowVal.equals("sat") || dowVal.equals("sun");
					}
				} else {
					DayOfWeek dow = ts.getDayOfWeek();
					isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
				}

				totals.spend += co;
				totals.imps += im;
				totals.clicks += cl;
				if (colCmp >= 0 && cmp > 0) {
					totals.completions += cmp;
					impsWithCompletions[0] += im;
					totals.hasCompletions = true;
				}

				if (!chVal.isEmpty()) {
					Agg a = byChannel.computeIfAbsent(chVal, k -> new Agg());
					a.spend += co;
					a.imps += im;
					a.clicks += cl;
					if (cmp > 0) {
						a.completions += cmp;
						a.hasCompletions = true;
					}
					if (isWeekend) {
						a.weekendImps += im;
					} else {
						a.weekdayImps += im;
					}
				}

				if (liId != null && liToTacticNum.containsKey(liId)) {
					Agg a = byLineItemId.computeIfAbsent(liId, k -> new Agg());
					a.spend += co;
					a.imps += im;
					a.clicks += cl;
					if (cmp > 0) {
						a.completions += cmp;
						a.hasCompletions = true;
					}
					if (isWeekend) {
						a.weekendImps += im;
					} else {
						a.weekdayImps += im;
					}

					if (colCr >= 0) {
						String crName = cellAt(row, colCr);
						if (!crName.isEmpty()) {
							double[] cr = byCreative.computeIfAbsent(liId, k -> new LinkedHashMap<>())
									.computeIfAbsent(crName, k -> new double[]{0.0, 0.0});
							cr[0] += im;
							cr[1] += cl;
						}
					}
				}
			}
		}

		Map<String, double[]> topCreativeByLi = new LinkedHashMap<>(); // liId -> {imps, clicks}
		Map<String, String> topCreativeName = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, double[]>> e : byCreative.entrySet()) {
			String topName = null;
			double topImps = -1.0;
			double topClicks = 0.0;
			for (Map.Entry<String, double[]> c : e.getValue().entrySet()) {
				if (c.getValue()[0] > topImps) {
					topImps = c.getValue()[0];
					topClicks = c.getValue()[1];
					topName = c.getKey();
				}
			}
			if (topName != null) {
				topCreativeByLi.put(e.getKey(), new double[]{topImps, topClicks});
				topCreativeName.put(e.getKey(), topName);
			}
		}

		Double totalCtr = totals.imps > 0 ? totals.clicks / totals.imps * 100 : null;
		Double totalVcr = (totals.hasCompletions && impsWithCompletions[0] > 0)
				? totals.completions / impsWithCompletions[0] * 100 : null;

		Map<Integer, Tactic> tacticsData = new LinkedHashMap<>();
		for (Map.Entry<Integer, String[]> e : tacticMap.entrySet()) {
			int n = e.getKey();
			String name = e.getValue()[0];
			String channel = e.getValue()[1];

			String liIdForTactic = numToLiId.get(n);
			Agg agg = null;
			if (liIdForTactic != null && byLineItemId.containsKey(liIdForTactic)) {
				agg = byLineItemId.get(liIdForTactic);
			} else {
				String ch = channel != null ? channel.trim().toLowerCase(Locale.ROOT) : null;
				if (ch != null && byChannel.containsKey(ch)) {
					agg = byChannel.get(ch);
				}
			}

			double sp = agg != null ? agg.spend : 0.0;
			double im = agg != null ? agg.imps : 0.0;
			double cl = agg != null ? agg.clicks : 0.0;
			double cmp = agg != null ? agg.completions : 0.0;
			double wdi = agg != null ? agg.weekdayImps : 0.0;
			double wei = agg != null ? agg.weekendImps : 0.0;

			Double tCtr = im > 0 ? cl / im * 100 : null;
			Double tVcr = (agg != null && agg.hasCompletions && im > 0) ? cmp / im * 100 : null;

			double totalDayImps = wdi + wei;
			Integer weekdaysPct = totalDayImps > 0 ? (int) Math.round(wdi / totalDayImps * 100) : null;
			Integer weekendsPct = weekdaysPct != null ? 100 - weekdaysPct : null;

			double[] plan = planByTacticNum.get(n);

			String topName = liIdForTactic != null ? topCreativeName.get(liIdForTactic) : null;
			double[] topCr = liIdForTactic != null ? topCreativeByLi.get(liIdForTactic) : null;

			tacticsData.put(n, new Tactic(
					name,
					channel,
					liIdForTactic,
					sp, im, cl, cmp,
					tCtr, tVcr,
					weekdaysPct, weekendsPct,
					plan != null ? nan(plan[0]) : null,
					plan != null ? nan(plan[1]) : null,
					plan != null ? nan(plan[2]) : null,
					plan != null ? nan(plan[3]) : null,
					plan != null ? nan(plan[4]) : null,
					topName,
					topCr != null ? topCr[0] : null,
					topCr != null ? topCr[1] : null,
					plan != null && plan.length > 5 ? nan(plan[5]) : null,
					plan != null && plan.length > 6 ? nan(plan[6]) : null
			));
		}

		return new WindowMetrics(
				new Totals(totals.spend, totals.imps, totals.clicks, totals.completions, totalCtr, totalVcr),
				tacticsData
		);
	}

	/**
	 * Resolves the report's date window from the user-confirmed filter, falling back to the full
	 * raw-data range. A {@code RANGE} filter clips delivery rows and the {@code {{flight_dates}}}
	 * placeholder to its own bounds; {@code ALL} or a {@code null} filter spans every date present in
	 * the raw data ("Basic" tab). The media plan is never consulted for dates.
	 *
	 * @param dateFilter user-selected filter (may be {@code null})
	 * @param adjRows    raw-data rows scanned for the full date range when no explicit range applies
	 * @return the inclusive window, or {@code null} when no dates can be detected
	 */
	FlightDates resolveDateWindow(DateFilter dateFilter, List<List<String>> adjRows) {

		if (dateFilter != null) {
			FlightDates window = dateFilter.toWindow();
			if (window != null) {
				return window;
			}
		}
		return sheetUtils.detectDataDateRange(adjRows);
	}

	// ── Estimates parser ──────────────────────────────────────────────────────

	/**
	 * Parses the Estimates tab into planned figures per tactic, preserving media-plan order. Each Media-column
	 * name maps to a FIFO queue of {@code {spend, imps, ctr, vcr, maxFreq}} rows (NaN where blank), one entry
	 * per line item in top-to-bottom order. A name repeated across line items (e.g. "Meta" appearing several
	 * times with different budgets) therefore keeps every occurrence's own numbers instead of collapsing to a
	 * single row, so the tactic loop can assign the N-th occurrence its N-th planned line item.
	 *
	 * @param estimatesRows the Estimates tab grid (may be empty)
	 * @return a map from lowercased tactic name to its ordered queue of planned-figure rows (never {@code null})
	 */
	Map<String, Deque<double[]>> parseEstimates(List<List<String>> estimatesRows) {
		Map<String, Deque<double[]>> out = new LinkedHashMap<>();
		if (estimatesRows.isEmpty()) {
			return out;
		}

		int eHdrIdx = -1;
		int eMediaCol = -1;
		int eCostCol = -1;
		int eImpsCol = -1;
		int eCtrCol = -1;
		int eVcrCol = -1;
		int eFreqCol = -1;
		for (int i = 0; i < estimatesRows.size(); i++) {
			List<String> row = estimatesRows.get(i);
			if (row == null) {
				continue;
			}
			boolean hasMedia = false;
			int media = -1;
			int cost = -1;
			int imps = -1;
			int ctr = -1;
			int vcr = -1;
			int freq = -1;
			for (int j = 0; j < row.size(); j++) {
				String v = cell(row, j).toLowerCase(Locale.ROOT);
				if (v.equals("media")) {
					hasMedia = true;
					media = j;
				}
				if (v.equals("total cost") || v.equals("cost") || v.equals("budget")) {
					cost = j;
				}
				if (v.equals("impressions") || v.equals("imps")) {
					imps = j;
				}
				if (v.equals("ctr")) {
					ctr = j;
				}
				if (v.equals("vcr") || v.equals("vtr") || v.equals("view rate") || v.equals("vcr / acr") || v.equals(
						"vcr/acr") || v.equals("acr")) {
					vcr = j;
				}
				if (v.contains("max frequency") || v.contains("frequency per flight")) {
					freq = j;
				}
			}
			if (hasMedia && (cost >= 0 || imps >= 0)) {
				eHdrIdx = i;
				eMediaCol = media;
				eCostCol = cost;
				eImpsCol = imps;
				eCtrCol = ctr;
				eVcrCol = vcr;
				eFreqCol = freq;
				break;
			}
		}
		if (eHdrIdx < 0) {
			return out;
		}

		for (int i = eHdrIdx + 1; i < estimatesRows.size(); i++) {
			List<String> row = estimatesRows.get(i);
			if (row == null) {
				continue;
			}
			String rowText = joinLower(row, 5);
			boolean stop = false;
			for (String sw : STOP_WORDS) {
				if (rowText.contains(sw)) {
					stop = true;
					break;
				}
			}
			if (stop) {
				break;
			}

			String mediaVal = cellAt(row, eMediaCol);
			if (mediaVal.isEmpty()) {
				continue;
			}

			double spend = parseNumericCell(cellAt(row, eCostCol), eCostCol, true);
			double imps = parseNumericCell(cellAt(row, eImpsCol), eImpsCol, false);
			double ctr = parseNumericCell(cellAt(row, eCtrCol), eCtrCol, false);
			double vcr = parseNumericCell(cellAt(row, eVcrCol), eVcrCol, false);
			double freq = parseNumericCell(cellAt(row, eFreqCol), eFreqCol, false);

			out.computeIfAbsent(mediaVal.toLowerCase(Locale.ROOT), k -> new ArrayDeque<>())
					.add(new double[]{spend, imps, ctr, vcr, freq});
		}
		return out;
	}

	/**
	 * Cleans then parses a cell to a numeric, returning {@code NaN} when blank/non-numeric.
	 */
	double parseNumericCell(String raw, int col, boolean allowMinus) {

		if (col < 0) {
			return Double.NaN;
		}
		String c = cleanNum(raw, allowMinus);
		return !c.isEmpty() && isNumeric(c) ? toFloat(c) : Double.NaN;
	}

	Double nan(double v) {

		return Double.isNaN(v) ? null : v;
	}

	// ── numeric helpers (string cleanup + float cast + numeric check) ───────

	private static final Pattern LEADING_NUM = Pattern.compile("^[-+]?\\d*\\.?\\d+");

	String cleanNum(String raw, boolean allowMinus) {

		if (raw == null) {
			return "";
		}
		String s = raw.replace(",", "");
		return s.replaceAll(allowMinus ? "[^0-9.\\-]" : "[^0-9.]", "");
	}

	double toFloat(String s) {

		if (s == null) {
			return 0.0;
		}
		Matcher m = LEADING_NUM.matcher(s.trim());
		if (m.find()) {
			try {
				return Double.parseDouble(m.group());
			} catch (NumberFormatException ignored) {
				return 0.0;
			}
		}
		return 0.0;
	}

	boolean isNumeric(String s) {

		return s != null && s.matches("[-+]?\\d*\\.?\\d+");
	}

	// ── cell helpers ──────────────────────────────────────────────────────────

	String coalesce(String a, String b) {

		return a != null ? a : b;
	}

	/**
	 * Joins the distinct values of a media-plan grid column into a comma-separated string for the
	 * campaign-data snapshot, or {@code null} when the column is absent.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param column    the media-plan column whose header synonyms locate the values
	 * @return the comma-separated column values, or {@code null} when no matching column is found
	 */
	String joinColumn(List<List<String>> sheetRows, MediaPlanColumn column) {

		List<String> values = sheetUtils.collectColumnValuesBelow(sheetRows, column.getSynonyms());
		return values.isEmpty() ? null : String.join(", ", values);
	}

	String cell(List<String> row, int idx) {

		String v = row.get(idx);
		return v == null ? "" : v.trim();
	}

	String cellAt(List<String> row, int idx) {

		if (row == null || idx < 0 || idx >= row.size()) {
			return "";
		}
		return cell(row, idx);
	}

	String joinLower(List<String> row, int n) {

		StringBuilder sb = new StringBuilder();
		int limit = Math.min(n, row.size());
		for (int i = 0; i < limit; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(cell(row, i));
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}
}
