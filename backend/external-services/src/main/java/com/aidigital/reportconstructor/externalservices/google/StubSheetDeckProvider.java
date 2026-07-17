package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.ports.PacingTablesRequest;
import com.aidigital.reportconstructor.service.reports.ports.SheetDeckProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic Sheets provider — the only candidate when no {@code @Primary}
 * real Sheets-deck bean is registered (i.e. when {@code GOOGLE_SERVICE_ACCOUNT_JSON}
 * is unset and {@link RealSheetDeckProvider} stays conditional-excluded).
 *
 * <p>Fabricates the template URL with the job-id suffix so the "Generate Sheet"
 * flow remains end-to-end runnable without Google access. Mirrors
 * {@link StubSlidesProvider}.
 */
@Component
@RequiredArgsConstructor
public class StubSheetDeckProvider implements SheetDeckProvider {

	private final GoogleProperties props;

	@Override
	public boolean isLive() {
		return false;
	}

	@Override
	public String createSheet(
			String jobId, String fileName, Map<String, String> placeholderMap, String userGoogleAccessToken) {
		return "https://docs.google.com/spreadsheets/d/" + props.getSheetsTemplateId() + "/edit?stub=" + jobId;
	}

	@Override
	public void trimTactics(String spreadsheetId, int tacticCount, String userGoogleAccessToken) {
		// No-op: the stub never clones a real workbook, so there are no ranges to clear.
	}

	@Override
	public List<String> writePacingTables(String spreadsheetId, PacingTablesRequest request) {
		// No-op: the stub never clones a real workbook, so there are no tables to write.
		return List.of();
	}

	@Override
	public List<List<String>> readSheetGrid(String spreadsheetId, String userGoogleAccessToken) {
		// No workbook is ever cloned offline, so there is no grid to read back.
		return List.of();
	}

	@Override
	public void clearBreakdowns(
			String spreadsheetId, Map<Integer, Set<BreakdownType>> enabledByTactic, String userGoogleAccessToken) {
		// No-op: the stub never clones a real workbook, so there is no "Breakdowns" tab to clear.
	}

	@Override
	public Map<Integer, List<PublisherRow>> readPublisherTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered publisher rows to read.
		return Map.of();
	}

	@Override
	public Map<Integer, CreativeTable> readCreativeTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered creative rows to read.
		return Map.of();
	}

	@Override
	public Map<Integer, GeoTable> readGeoTables(
			String spreadsheetId, Set<Integer> tacticNums, String userGoogleAccessToken) {
		// The stub never clones a real workbook, so there are no hand-entered geo rows to read.
		return Map.of();
	}
}
