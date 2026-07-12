package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetCampaignReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Spring bean implementation of {@link SheetCampaignReader}. Pure logic over the placeholder map —
 * no Google or grid access — so it is unit-testable in isolation.
 */
@Component
@RequiredArgsConstructor
public class SheetCampaignReaderImpl implements SheetCampaignReader {

	/** Max tactics the report template carries; the requested tactic count is clamped to this. */
	private static final int MAX_TACTICS = 28;

	private final ReportNumberParser numbers;

	@Override
	public CampaignData read(Map<String, String> flat, int tacticCount) {
		int count = Math.clamp(tacticCount, 0, MAX_TACTICS);
		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		for (int n = 1; n <= count; n++) {
			tactics.put(n, readTactic(flat, n));
		}
		Totals totals = new Totals(
				num(flat, "{{total_investment}}"),
				num(flat, "{{total imps}}"),
				num(flat, "{{total clicks}}"),
				num(flat, "{{total complitions}}"),
				nullableNum(flat, "{{total ctr}}"),
				nullableNum(flat, "{{total vcr}}"));
		return new CampaignData(
				str(flat, "{{client_name}}"),
				str(flat, "{{Campaign_name}}"),
				str(flat, "{{geo_locations}}"),
				str(flat, "{{funnel_stages}}"),
				str(flat, "{{flight_dates}}"),
				null,
				str(flat, "{{total_investment}}"),
				str(flat, "{{primary_kpis}}"),
				str(flat, "{{tactics_list}}"),
				str(flat, "{{audience_age}}"),
				str(flat, "{{audience_segments}}"),
				totals,
				tactics,
				null);
	}

	@Override
	public CampaignFrequencies readFrequencies(Map<String, String> flat) {
		double imps = num(flat, "{{total imps}}");
		double reach = num(flat, "{{reach}}");
		if (imps <= 0 || reach <= 0) {
			return new CampaignFrequencies(null, null, null, null);
		}
		String plan = String.valueOf((long) Math.ceil(imps / reach));

		// The sheet already carries the actual frequency the user reviewed ({{reach_f}} — the summary
		// "Frequency" total). Reading it back keeps the Claude narrative and the deck in sync with the
		// sheet instead of re-deriving it from a fresh random reach uplift. When it is missing we still
		// return the deterministic plan so the narrative can at least reference the planned figure.
		Double factFreq = nullableNum(flat, "{{reach_f}}");
		if (factFreq == null || factFreq <= 0) {
			return new CampaignFrequencies(plan, null, null, null);
		}
		String fact = String.format(Locale.US, "%.2f", factFreq);
		double reachFact = imps / factFreq;
		Double marketVolume = nullableNum(flat, "{{market volume}}");
		Double remainingAudience = marketVolume == null ? null : Math.max(marketVolume - reachFact, 0);
		return new CampaignFrequencies(plan, fact, reachFact, remainingAudience);
	}

	/**
	 * Reconstructs one tactic's metrics from its {@code {{tactic n ...}}} placeholders. Frequency,
	 * daypart and line-item fields are left null: they are not read by the Claude prompts and the
	 * daypart copy already lives in the sheet.
	 *
	 * @param flat the placeholder map
	 * @param n    1-based tactic number
	 * @return the reconstructed tactic
	 */
	Tactic readTactic(Map<String, String> flat, int n) {
		String prefix = "{{tactic " + n;
		String name = str(flat, prefix + "}}");
		return new Tactic(
				name,
				name,
				null,
				num(flat, prefix + " spend}}"),
				num(flat, prefix + " imps}}"),
				num(flat, prefix + " clicks}}"),
				num(flat, prefix + " complitions}}"),
				nullableNum(flat, prefix + " ctr}}"),
				nullableNum(flat, prefix + " vcr}}"),
				null,
				null,
				nullableNum(flat, prefix + " spend plan}}"),
				nullableNum(flat, prefix + " imps plan}}"),
				nullableNum(flat, prefix + " ctr plan}}"),
				nullableNum(flat, prefix + " vcr plan}}"),
				null,
				str(flat, prefix + " top creative name}}"),
				nullableNum(flat, prefix + " top creative imps}}"),
				nullableNum(flat, prefix + " top creative clicks}}"));
	}

	/**
	 * Returns the trimmed placeholder value, or an empty string when absent.
	 *
	 * @param flat the placeholder map
	 * @param key  the {@code {{token}}} key
	 * @return the value, never {@code null}
	 */
	String str(Map<String, String> flat, String key) {
		String v = flat.get(key);
		return v == null ? "" : v.trim();
	}

	/**
	 * Parses a placeholder value as a primitive double (0.0 when blank/unparseable).
	 *
	 * @param flat the placeholder map
	 * @param key  the {@code {{token}}} key
	 * @return the parsed value, or 0.0
	 */
	double num(Map<String, String> flat, String key) {
		return numbers.parseReportNumber(flat.get(key));
	}

	/**
	 * Parses a placeholder value as a boxed double, returning {@code null} when the cell is blank so
	 * "no data" stays distinct from a real zero in the Claude prompt context.
	 *
	 * @param flat the placeholder map
	 * @param key  the {@code {{token}}} key
	 * @return the parsed value, or {@code null} when blank
	 */
	Double nullableNum(Map<String, String> flat, String key) {
		String v = flat.get(key);
		if (v == null || v.isBlank()) {
			return null;
		}
		return numbers.parseReportNumber(v);
	}
}
