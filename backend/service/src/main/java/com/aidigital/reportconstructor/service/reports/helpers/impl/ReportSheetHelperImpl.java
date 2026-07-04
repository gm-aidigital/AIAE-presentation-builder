package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import com.aidigital.reportconstructor.service.reports.ports.SheetDeckProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link ReportSheetHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportSheetHelperImpl implements ReportSheetHelper {

	private static final Pattern SPREADSHEET_ID = Pattern.compile("/d/([a-zA-Z0-9_-]+)");

	private final SheetDeckProvider sheets;
	private final TacticExtractionHelper tacticExtraction;
	private final ReportNumberParser reportNumbers;

	@Override
	public String buildSheet(String jobId, Map<String, String> flatReplacements, String userGoogleToken) {
		return sheets.createSheet(jobId, flatReplacements, userGoogleToken);
	}

	@Override
	public void trimUnusedTactics(String sheetUrl, GeneratePayload payload, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			return;
		}
		int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);
		try {
			sheets.trimTactics(spreadsheetId, tacticCount, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] trimTactics failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
		}
	}

	@Override
	public List<String> writePacingTables(
			String sheetUrl, GeneratePayload payload, CampaignData data,
			Map<String, String> flatReplacements, String userGoogleToken) {
		if (payload.bqSheetId() == null || payload.bqSheetId().isBlank()
				|| payload.adjRows() == null || payload.adjRows().isEmpty()
				|| payload.lineItemMapping() == null || payload.lineItemMapping().isEmpty()) {
			return List.of();
		}
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			return List.of("Pacing tables skipped — could not determine spreadsheet id from " + sheetUrl);
		}

		int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);

		Map<Integer, String> distNames = new LinkedHashMap<>();
		Map<Integer, Double> distImps = new LinkedHashMap<>();
		Map<Integer, String> kpiTypes = new LinkedHashMap<>();
		for (int n = 1; n <= tacticCount; n++) {
			String name = firstNonBlank(flatReplacements.get("{{tactic " + n + "}}"), "Tactic " + n);
			distNames.put(n, name);
			distImps.put(n, reportNumbers.parseReportNumber(flatReplacements.get("{{tactic " + n + " imps}}")));
			kpiTypes.put(n, tacticExtraction.getTacticKpiType(name));
		}
		double totalImps = reportNumbers.parseReportNumber(flatReplacements.get("{{total imps}}"));

		try {
			return sheets.writePacingTables(spreadsheetId, new PacingTablesRequest(
					payload.adjRows(),
					payload.lineItemMapping(),
					data.flightTs(),
					tacticCount,
					distNames,
					distImps,
					totalImps,
					kpiTypes,
					userGoogleToken
			));
		} catch (RuntimeException ex) {
			log.error("[sheets] pacing tables step failed for {}", spreadsheetId, ex);
			return List.of("Pacing tables failed: " + ex.getMessage());
		}
	}

	/**
	 * Parses the spreadsheet id out of a Google Sheets URL (the {@code /d/<id>} segment).
	 *
	 * @param sheetUrl the generated Sheets URL, possibly null
	 * @return the spreadsheet id, or {@code null} when the URL is null or unparseable
	 */
	String extractSpreadsheetId(String sheetUrl) {
		if (sheetUrl == null) {
			return null;
		}
		Matcher m = SPREADSHEET_ID.matcher(sheetUrl);
		return m.find() ? m.group(1) : null;
	}

	/**
	 * Returns the first non-blank, non-em-dash value.
	 *
	 * @param values candidate values in priority order
	 * @return the first usable value, or an empty string when none qualify
	 */
	String firstNonBlank(String... values) {
		for (String v : values) {
			if (v != null && !v.isBlank() && !"—".equals(v.trim())) {
				return v.trim();
			}
		}
		return "";
	}
}
