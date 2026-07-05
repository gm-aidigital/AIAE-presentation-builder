package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One tactic's plan/fact figures read back from a generated report workbook's per-tactic
 * summary table. Values are already-formatted display strings taken verbatim from the sheet
 * cells; a field is {@code null} when that cell was never filled (a leftover template token
 * is treated as absent, not a value).
 *
 * @param tactic          the tactic name as shown in the summary table
 * @param impressionsPlan the planned impressions cell, or {@code null} when absent
 * @param impressionsFact the delivered (fact) impressions cell, or {@code null} when absent
 * @param spendPlan       the planned spend cell, or {@code null} when absent
 * @param spendFact       the delivered (fact) spend cell, or {@code null} when absent
 */
public record SheetSummaryRow(
		String tactic,
		String impressionsPlan,
		String impressionsFact,
		String spendPlan,
		String spendFact
) {
	// required
}
