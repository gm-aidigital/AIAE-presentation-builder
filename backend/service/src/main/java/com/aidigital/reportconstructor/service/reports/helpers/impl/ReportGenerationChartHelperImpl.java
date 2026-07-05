package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.SheetChartData;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetChartDataReader;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link ReportGenerationChartHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationChartHelperImpl implements ReportGenerationChartHelper {

	private static final Pattern PRESENTATION_ID = Pattern.compile("/d/([a-zA-Z0-9_-]+)");

	private final ChartProvider charts;
	private final SlidesProvider slides;
	private final TacticExtractionHelper tacticExtraction;
	private final ReportNumberParser reportNumbers;
	private final SheetChartDataReader sheetChartData;

	@Override
	public List<String> buildCharts(
			String slideUrl,
			GeneratePayload payload,
			CampaignData data,
			Map<String, String> flatReplacements,
			String userGoogleToken
	) {
		if (payload.bqSheetId() == null || payload.bqSheetId().isBlank()
				|| payload.adjRows() == null || payload.adjRows().isEmpty()
				|| payload.lineItemMapping() == null || payload.lineItemMapping().isEmpty()) {
			return List.of();
		}
		String presentationId = extractPresentationId(slideUrl);
		if (presentationId == null) {
			return List.of("Charts skipped — could not determine presentation id from " + slideUrl);
		}

		int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);
		String campaignTitle = campaignTitle(flatReplacements);

		Map<Integer, String> distNames = new LinkedHashMap<>();
		Map<Integer, Double> distImps = new LinkedHashMap<>();
		Map<Integer, String> kpiTypes = new LinkedHashMap<>();
		populateTacticMaps(flatReplacements, tacticCount, distNames, distImps, kpiTypes);
		double totalImps = reportNumbers.parseReportNumber(flatReplacements.get("{{total imps}}"));

		try {
			return charts.buildCharts(new ChartRequest(
					presentationId,
					payload.adjRows(),
					payload.lineItemMapping(),
					data.flightTs(),
					tacticCount,
					campaignTitle,
					distNames,
					distImps,
					totalImps,
					kpiTypes,
					userGoogleToken,
					null,
					null
			));
		} catch (RuntimeException ex) {
			log.error("[charts] chart step failed for presentation {}", presentationId, ex);
			return List.of("Charts failed: " + ex.getMessage());
		}
	}

	@Override
	public List<String> buildChartsFromSheet(
			String slideUrl,
			List<List<String>> grid,
			Map<String, String> flatReplacements,
			int tacticCount,
			String userGoogleToken
	) {
		String presentationId = extractPresentationId(slideUrl);
		if (presentationId == null) {
			return List.of("Charts skipped — could not determine presentation id from " + slideUrl);
		}
		int count = Math.clamp(tacticCount, 1, 7);

		Map<Integer, String> distNames = new LinkedHashMap<>();
		Map<Integer, Double> distImps = new LinkedHashMap<>();
		Map<Integer, String> kpiTypes = new LinkedHashMap<>();
		populateTacticMaps(flatReplacements, count, distNames, distImps, kpiTypes);
		double totalImps = reportNumbers.parseReportNumber(flatReplacements.get("{{total imps}}"));

		// The pacing series are read straight from the (user-edited) sheet — no BigQuery — and the
		// KPI types drive whether each block's single metric column is read as clicks or completions.
		SheetChartData chartData = sheetChartData.read(grid, count, kpiTypes);

		log.info("[charts] sheet flow: presentation={}, tactics={}, gridRows={}, dailyPivotSizes={}, monthlyPivotSizes={}",
				presentationId, count, grid == null ? 0 : grid.size(),
				pivotSizes(chartData.dailyPivots()), pivotSizes(chartData.monthlyPivots()));

		try {
			List<String> chartWarnings = charts.buildCharts(new ChartRequest(
					presentationId,
					List.of(),
					List.of(),
					null,
					count,
					campaignTitle(flatReplacements),
					distNames,
					distImps,
					totalImps,
					kpiTypes,
					userGoogleToken,
					chartData.dailyPivots(),
					chartData.monthlyPivots()
			));
			log.info("[charts] sheet flow finished for presentation {} with {} warning(s): {}",
					presentationId, chartWarnings.size(), chartWarnings);
			return chartWarnings;
		} catch (RuntimeException ex) {
			log.error("[charts] sheet chart step failed for presentation {}", presentationId, ex);
			return List.of("Charts failed: " + ex.getMessage());
		}
	}

	/**
	 * Renders the per-tactic pivot row counts as a compact {@code {tactic=rows}} map for diagnostic
	 * logging, so an empty read-back (which silently skips a chart) is visible in the logs.
	 *
	 * @param pivots tactic number &rarr; its reconstructed pacing pivot
	 * @return tactic number &rarr; the pivot's data-row count
	 */
	Map<Integer, Integer> pivotSizes(Map<Integer, Pivot> pivots) {
		Map<Integer, Integer> sizes = new LinkedHashMap<>();
		pivots.forEach((n, pivot) -> sizes.put(n, pivot.data().size()));
		return sizes;
	}

	/**
	 * Fills the distribution/KPI maps for tactics 1..{@code tacticCount} from the resolved placeholder
	 * values, shared by the BigQuery and sheet chart paths.
	 *
	 * @param flatReplacements resolved placeholder values keyed by token
	 * @param tacticCount      number of active tactics
	 * @param distNames        out: tactic number &rarr; display name
	 * @param distImps         out: tactic number &rarr; impressions
	 * @param kpiTypes         out: tactic number &rarr; KPI type derived from the tactic's channel name
	 */
	void populateTacticMaps(
			Map<String, String> flatReplacements, int tacticCount,
			Map<Integer, String> distNames, Map<Integer, Double> distImps, Map<Integer, String> kpiTypes) {
		for (int n = 1; n <= tacticCount; n++) {
			String name = firstNonBlank(flatReplacements.get("{{tactic " + n + "}}"), "Tactic " + n);
			distNames.put(n, name);
			distImps.put(n, reportNumbers.parseReportNumber(flatReplacements.get("{{tactic " + n + " imps}}")));
			kpiTypes.put(n, tacticExtraction.getTacticKpiType(name));
		}
	}

	/**
	 * Resolves the deck title from the campaign or client name, defaulting to {@code "Campaign"}.
	 *
	 * @param flatReplacements resolved placeholder values keyed by token
	 * @return the campaign title used for chart folder/file names
	 */
	String campaignTitle(Map<String, String> flatReplacements) {
		return firstNonBlank(
				flatReplacements.get("{{Campaign_name}}"),
				flatReplacements.get("{{client_name}}"),
				"Campaign");
	}

	@Override
	public void trimUnusedTactics(String slideUrl, GeneratePayload payload, String userGoogleToken) {
		int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);
		trimUnusedTactics(slideUrl, tacticCount, userGoogleToken);
	}

	@Override
	public void trimUnusedTactics(String slideUrl, int tacticCount, String userGoogleToken) {
		String presentationId = extractPresentationId(slideUrl);
		if (presentationId == null) {
			return;
		}
		int clamped = Math.clamp(tacticCount, 1, 7);
		try {
			slides.trimTactics(presentationId, clamped, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[slides] trimTactics failed for {} (non-fatal): {}", presentationId, ex.getMessage());
		}
	}

	String extractPresentationId(String slideUrl) {
		if (slideUrl == null) {
			return null;
		}
		Matcher m = PRESENTATION_ID.matcher(slideUrl);
		return m.find() ? m.group(1) : null;
	}

	String firstNonBlank(String... values) {
		for (String v : values) {
			if (v != null && !v.isBlank() && !"—".equals(v.trim())) {
				return v.trim();
			}
		}
		return "";
	}
}
