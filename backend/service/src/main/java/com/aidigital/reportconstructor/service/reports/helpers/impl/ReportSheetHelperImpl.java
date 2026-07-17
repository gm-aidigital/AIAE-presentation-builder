package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
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
import java.util.Set;
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

	/** Max tactics the report template carries; media-plan tactic counts are clamped to this. */
	private static final int MAX_TACTICS = 28;

	private final SheetDeckProvider sheets;
	private final TacticExtractionHelper tacticExtraction;
	private final ReportNumberParser reportNumbers;
	private final BreakdownSelectionResolver breakdownResolver;

	@Override
	public String buildSheet(String jobId, String fileName, Map<String, String> flatReplacements, String userGoogleToken) {
		return sheets.createSheet(jobId, fileName, flatReplacements, userGoogleToken);
	}

	@Override
	public void trimUnusedTactics(String sheetUrl, GeneratePayload payload, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			return;
		}
		int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, MAX_TACTICS);
		try {
			sheets.trimTactics(spreadsheetId, tacticCount, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] trimTactics failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
		}
	}

	@Override
	public void clearUnselectedBreakdowns(String sheetUrl, GeneratePayload payload, String userGoogleToken) {
		if (payload.breakdownSelections() == null) {
			return;
		}
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			return;
		}
		Map<Integer, Set<BreakdownType>> enabledByTactic = toEnabledByTactic(payload.breakdownSelections());
		try {
			sheets.clearBreakdowns(spreadsheetId, enabledByTactic, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] clearBreakdowns failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
		}
	}

	/**
	 * Reduces the raw per-tactic breakdown selections to a map of 1-based tactic number to the set of
	 * enabled breakdown sections, dropping unknown/blank section codes and null tactic numbers.
	 *
	 * @param selections the per-tactic breakdown selections from the request (never null)
	 * @return tactic number → enabled breakdown sections (empty set when a tactic enabled none)
	 */
	Map<Integer, Set<BreakdownType>> toEnabledByTactic(List<BreakdownSelection> selections) {
		return breakdownResolver.resolve(selections);
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

		int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, MAX_TACTICS);

		Map<Integer, String> distNames = new LinkedHashMap<>();
		Map<Integer, Double> distImps = new LinkedHashMap<>();
		Map<Integer, String> kpiTypes = new LinkedHashMap<>();
		for (int n = 1; n <= tacticCount; n++) {
			String name = firstNonBlank(flatReplacements.get("{{tactic " + n + "}}"), "Tactic " + n);
			distNames.put(n, name);
			distImps.put(n, reportNumbers.parseReportNumber(flatReplacements.get("{{tactic " + n + " imps}}")));
			kpiTypes.put(n, tacticExtraction.getTacticKpiSeries(name));
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

	@Override
	public List<List<String>> readSheetGrid(String sheetUrl, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			log.warn("[sheets] readSheetGrid: could not determine spreadsheet id from {}", sheetUrl);
			return List.of();
		}
		return sheets.readSheetGrid(spreadsheetId, userGoogleToken);
	}

	@Override
	public Map<Integer, List<PublisherRow>> readPublisherTables(
			String sheetUrl, Set<Integer> tacticNums, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			log.warn("[sheets] readPublisherTables: could not determine spreadsheet id from {}", sheetUrl);
			return Map.of();
		}
		try {
			return sheets.readPublisherTables(spreadsheetId, tacticNums, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] readPublisherTables failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
			return Map.of();
		}
	}

	@Override
	public Map<Integer, CreativeTable> readCreativeTables(
			String sheetUrl, Set<Integer> tacticNums, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			log.warn("[sheets] readCreativeTables: could not determine spreadsheet id from {}", sheetUrl);
			return Map.of();
		}
		try {
			return sheets.readCreativeTables(spreadsheetId, tacticNums, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] readCreativeTables failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
			return Map.of();
		}
	}

	@Override
	public Map<Integer, GeoTable> readGeoTables(
			String sheetUrl, Set<Integer> tacticNums, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			log.warn("[sheets] readGeoTables: could not determine spreadsheet id from {}", sheetUrl);
			return Map.of();
		}
		try {
			return sheets.readGeoTables(spreadsheetId, tacticNums, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] readGeoTables failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
			return Map.of();
		}
	}

	@Override
	public Map<Integer, AudienceTable> readAudienceTables(
			String sheetUrl, Set<Integer> tacticNums, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			log.warn("[sheets] readAudienceTables: could not determine spreadsheet id from {}", sheetUrl);
			return Map.of();
		}
		try {
			return sheets.readAudienceTables(spreadsheetId, tacticNums, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] readAudienceTables failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
			return Map.of();
		}
	}

	@Override
	public Map<Integer, DeviceTable> readDeviceTables(
			String sheetUrl, Set<Integer> tacticNums, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			log.warn("[sheets] readDeviceTables: could not determine spreadsheet id from {}", sheetUrl);
			return Map.of();
		}
		try {
			return sheets.readDeviceTables(spreadsheetId, tacticNums, userGoogleToken);
		} catch (RuntimeException ex) {
			log.warn("[sheets] readDeviceTables failed for {} (non-fatal): {}", spreadsheetId, ex.getMessage());
			return Map.of();
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
