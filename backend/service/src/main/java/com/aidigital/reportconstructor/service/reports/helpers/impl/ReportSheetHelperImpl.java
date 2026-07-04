package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.SheetDeckProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}
