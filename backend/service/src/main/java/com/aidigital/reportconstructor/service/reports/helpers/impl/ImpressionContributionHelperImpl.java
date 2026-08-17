package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.helpers.ImpressionContributionHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring bean implementation of {@link ImpressionContributionHelper}.
 */
@Component
@RequiredArgsConstructor
public class ImpressionContributionHelperImpl implements ImpressionContributionHelper {

	/** Campaign-level impressions token the share is taken against. */
	static final String TOTAL_IMPS_TOKEN = "{{total imps}}";

	/** Em dash written when the share cannot be computed, matching the deck's other unresolved values. */
	static final String DASH = "—";

	/** A full share; a tactic can never contribute more than the campaign total. */
	static final double FULL_SHARE = 100.0;

	private final ReportNumberParser reportNumbers;
	private final Fmt fmt;

	@Override
	public void fillContributions(Map<String, String> flatReplacements, int tacticCount) {
		if (flatReplacements == null) {
			return;
		}
		double totalImps = reportNumbers.parseReportNumber(flatReplacements.get(TOTAL_IMPS_TOKEN));
		for (int n = 1; n <= tacticCount; n++) {
			double tacticImps = reportNumbers.parseReportNumber(flatReplacements.get("{{tactic " + n + " imps}}"));
			putIfBlank(flatReplacements, "{{tactic " + n + " contr}}", share(tacticImps, totalImps));
			putIfBlank(flatReplacements, "{{tactic " + n + " other contr}}", remainder(tacticImps, totalImps));
		}
	}

	/**
	 * Renders one tactic's share of the campaign's impressions.
	 *
	 * @param tacticImps the tactic's impressions
	 * @param totalImps  the campaign's impressions
	 * @return the share as a one-decimal percentage, or an em dash when either figure is unusable
	 */
	String share(double tacticImps, double totalImps) {
		Double share = sharePercent(tacticImps, totalImps);
		return share == null ? DASH : fmt.pct1(share);
	}

	/**
	 * Renders everything the tactic did not contribute — the pie's "Other" slice.
	 *
	 * <p>Taken from the already-rounded share rather than from the raw ratio, so the two legend lines always
	 * add up to 100.0% instead of drifting to 100.1% on a value like 34.25%.
	 *
	 * @param tacticImps the tactic's impressions
	 * @param totalImps  the campaign's impressions
	 * @return the remaining share as a one-decimal percentage, or an em dash when either figure is unusable
	 */
	String remainder(double tacticImps, double totalImps) {
		Double share = sharePercent(tacticImps, totalImps);
		return share == null ? DASH : fmt.pct1(FULL_SHARE - share);
	}

	/**
	 * Computes a tactic's share of the campaign's impressions, rounded to the one decimal the legend prints
	 * and capped at 100%.
	 *
	 * @param tacticImps the tactic's impressions
	 * @param totalImps  the campaign's impressions
	 * @return the share in percent, or {@code null} when either figure is missing or non-positive
	 */
	Double sharePercent(double tacticImps, double totalImps) {
		if (tacticImps <= 0 || totalImps <= 0) {
			return null;
		}
		double share = Math.min(FULL_SHARE, tacticImps / totalImps * FULL_SHARE);
		return Math.round(share * 10.0) / 10.0;
	}

	/**
	 * Writes a value only when the token has none yet, so a figure that came from the reviewed workbook is
	 * never overwritten by the computed one.
	 *
	 * @param flatReplacements the map being filled
	 * @param token            the token to write
	 * @param value            the computed value
	 */
	void putIfBlank(Map<String, String> flatReplacements, String token, String value) {
		String existing = flatReplacements.get(token);
		if (existing == null || existing.isBlank()) {
			flatReplacements.put(token, value);
		}
	}
}
