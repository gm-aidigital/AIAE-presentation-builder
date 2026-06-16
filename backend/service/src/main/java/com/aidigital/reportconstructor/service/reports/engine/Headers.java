package com.aidigital.reportconstructor.service.reports.engine;

/**
 * Zero-based column indices parsed from the BQ export header row; {@code -1} when a column is absent.
 *
 * @param dateCol        index of the {@code Date} column
 * @param impsCol        index of the {@code Impressions} column
 * @param clicksCol      index of the {@code Clicks} column, or {@code -1}
 * @param completionsCol index of the {@code Completions} column, or {@code -1}
 * @param l1NamingCol    index of the {@code Level 1 Naming} column, or {@code -1}
 */
public record Headers(int dateCol, int impsCol, int clicksCol, int completionsCol, int l1NamingCol) {

	/**
	 * @return {@code true} when both the {@code Date} and {@code Impressions} columns were located.
	 */
	public boolean valid() {
		return dateCol >= 0 && impsCol >= 0;
	}
}
