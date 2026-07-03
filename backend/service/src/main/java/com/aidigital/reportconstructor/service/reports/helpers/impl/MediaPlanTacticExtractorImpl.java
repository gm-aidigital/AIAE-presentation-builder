package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.engine.TacticCatalog;
import com.aidigital.reportconstructor.service.reports.helpers.MediaPlanTacticExtractor;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Locates the "Media" header, then walks the rows below it keeping only whitelisted tactic names.
 *
 * <p>Rows with an empty Media cell are treated as section/group labels (remembered as the context
 * for the tactics beneath them); rows whose Media cell is not a recognised tactic — sub-totals such
 * as {@code PRODUCT TOTALS}, added-value/reporting lines — are skipped, never used as a terminator.
 * The recognised-tactic whitelist is owned by {@link TacticCatalog}, shared with every other tactic
 * lookup, so the matcher and the collector agree on which rows are tactics and in what order.
 */
@Component
public class MediaPlanTacticExtractorImpl implements MediaPlanTacticExtractor {

	/**
	 * Cap on the number of tactics pulled from the Media column — one per report tactic slot.
	 */
	private static final int MAX_TACTICS = 7;

	private final TacticCatalog catalog;
	private final SheetRowHelper sheetRows;

	public MediaPlanTacticExtractorImpl(TacticCatalog catalog, SheetRowHelper sheetRows) {
		this.catalog = catalog;
		this.sheetRows = sheetRows;
	}

	@Override
	public List<PlanTactic> extract(List<List<String>> planRows) {

		List<PlanTactic> tactics = new ArrayList<>();
		if (planRows == null || planRows.isEmpty()) {
			return tactics;
		}

		int mediaRow = -1;
		int mediaCol = -1;
		outer:
		for (int i = 0; i < planRows.size(); i++) {
			List<String> row = planRows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (sheetRows.cellAt(row, j).equalsIgnoreCase("media")) {
					mediaRow = i;
					mediaCol = j;
					break outer;
				}
			}
		}
		if (mediaRow < 0) {
			return tactics;
		}

		String group = "";
		for (int i = mediaRow + 1; i < planRows.size(); i++) {
			List<String> row = planRows.get(i);
			String value = sheetRows.cellAt(row, mediaCol);
			if (value.isEmpty()) {
				// Section-label rows (e.g. "POUCHES", "PRODUCT TOTALS") have an empty Media cell but
				// a label elsewhere; remember it as the group for the tactics beneath it.
				String label = firstNonEmpty(row);
				if (!label.isEmpty()) {
					group = label;
				}
				continue;
			}
			// Non-tactic rows (sub-totals, added-value lines) are skipped rather than stopping the
			// scan, so grouped plans keep every tactic block instead of just the first.
			if (!catalog.isKnownTactic(value)) {
				continue;
			}
			tactics.add(new PlanTactic(value, buildContext(group, row, mediaCol)));
			if (tactics.size() >= MAX_TACTICS) {
				break;
			}
		}
		return tactics;
	}

	/**
	 * Joins the current group label with the tactic row's other non-empty cells into a single
	 * context string (Media cell excluded, whitespace collapsed).
	 *
	 * @param group    the most recent section/group label ("" when none)
	 * @param row      the tactic row
	 * @param mediaCol the Media column index to skip
	 * @return the joined context string
	 */
	String buildContext(String group, List<String> row, int mediaCol) {

		List<String> parts = new ArrayList<>();
		if (!group.isEmpty()) {
			parts.add(group);
		}
		int size = row == null ? 0 : row.size();
		for (int j = 0; j < size; j++) {
			if (j == mediaCol) {
				continue;
			}
			String c = sheetRows.cellAt(row, j);
			if (!c.isEmpty()) {
				parts.add(c);
			}
		}
		return String.join(" · ", parts).replaceAll("\\s+", " ").trim();
	}

	/**
	 * Returns the first non-empty, trimmed cell of a row, or "" when the row is empty/blank.
	 *
	 * @param row the row to scan
	 * @return the first non-empty cell value, or ""
	 */
	String firstNonEmpty(List<String> row) {

		int size = row == null ? 0 : row.size();
		for (int j = 0; j < size; j++) {
			String c = sheetRows.cellAt(row, j);
			if (!c.isEmpty()) {
				return c;
			}
		}
		return "";
	}
}
