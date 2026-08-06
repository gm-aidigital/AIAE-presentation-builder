package com.aidigital.reportconstructor.service.reports.helpers;

import java.util.Map;

/**
 * Counts how many tactics a filled workbook actually reports, from the placeholder values read
 * back off its first tab.
 */
public interface SheetTacticCountHelper {

	/** Max tactics the report template carries; no workbook can report more than this. */
	int MAX_TACTICS = 28;

	/**
	 * Counts the reported tactics: {@code {{tactic 1}}}, {@code {{tactic 2}}} … until the first
	 * blank. Counting stops at the gap rather than scanning on, because the deck numbers its
	 * tactics densely — a name after a hole would be rendered under the wrong number.
	 *
	 * @param flatReplacements the workbook's placeholder values
	 * @return the number of consecutively named tactics, {@code 0} when the workbook names none
	 */
	int countFromPlaceholders(Map<String, String> flatReplacements);
}
