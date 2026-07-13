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

	/** Matches a per-tactic token, capturing its 1-based slot number: {@code {{tactic 3 ctr}}} → 3. */
	private static final Pattern TACTIC_TOKEN = Pattern.compile("\\{\\{tactic (\\d+)");

	/** Max tactics the report template carries; media-plan tactic counts are clamped to this. */
	private static final int MAX_TACTICS = 28;

	private final SheetDeckProvider sheets;
	private final TacticExtractionHelper tacticExtraction;
	private final ReportNumberParser reportNumbers;

	@Override
	public String buildSheet(String jobId, String fileName, Map<String, String> flatReplacements,
			GeneratePayload payload, String userGoogleToken) {
		return sheets.createSheet(jobId, fileName, stripUnusedTacticTokens(flatReplacements, payload), userGoogleToken);
	}

	/**
	 * Drops per-tactic tokens ({@code {{tactic N …}}}) for slots above the Media Plan's real
	 * tactic count. The template's unused tactic slots are cleared wholesale by
	 * {@link #trimUnusedTactics} right after creation, so leaving their markers unreplaced is
	 * harmless — and pruning them keeps the create-sheet find/replace batch from carrying all
	 * 28 slots' worth of tokens for a small campaign (the batch scanned every one and pushed
	 * large reports past the Sheets read timeout).
	 *
	 * @param flatReplacements the full resolved placeholder map
	 * @param payload          generation request whose Media Plan drives the tactic count
	 * @return the map without tokens for tactic slots above the real count; the original map
	 *         when the campaign already uses the full {@link #MAX_TACTICS} slots
	 */
	Map<String, String> stripUnusedTacticTokens(Map<String, String> flatReplacements, GeneratePayload payload) {
		int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, MAX_TACTICS);
		if (tacticCount >= MAX_TACTICS) {
			return flatReplacements;
		}
		Map<String, String> kept = new LinkedHashMap<>(flatReplacements.size());
		for (Map.Entry<String, String> e : flatReplacements.entrySet()) {
			Matcher m = TACTIC_TOKEN.matcher(e.getKey());
			if (m.find() && Integer.parseInt(m.group(1)) > tacticCount) {
				continue;
			}
			kept.put(e.getKey(), e.getValue());
		}
		return kept;
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

	@Override
	public List<List<String>> readSheetGrid(String sheetUrl, String userGoogleToken) {
		String spreadsheetId = extractSpreadsheetId(sheetUrl);
		if (spreadsheetId == null) {
			log.warn("[sheets] readSheetGrid: could not determine spreadsheet id from {}", sheetUrl);
			return List.of();
		}
		return sheets.readSheetGrid(spreadsheetId, userGoogleToken);
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
