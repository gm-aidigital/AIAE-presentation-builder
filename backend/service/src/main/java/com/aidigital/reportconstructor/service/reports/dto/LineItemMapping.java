package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Links one media-plan tactic to its BigQuery line item so chart data can be queried per tactic,
 * plus the EOM-only rate/budget economics entered by the user for that tactic while matching.
 *
 * @param tactic        display name of the tactic as it appears in the Media Plan
 * @param lineItemId    BigQuery line-item identifier whose export rows feed this tactic's charts
 * @param tacticNum     1-based tactic slot number (1-7) used to position the tactic on the slide
 * @param rateType      EOM-only: how this tactic's cost is bought (CPM/CPC/CPV); {@code null} for EOC
 * @param unitPrice     EOM-only: the final unit price entered by the user for {@code rateType}; {@code null} for EOC
 * @param monthlyBudget EOM-only: the monthly budget entered by the user for this tactic; {@code null} for EOC
 */
public record LineItemMapping(
		String tactic,
		String lineItemId,
		Integer tacticNum,
		RateType rateType,
		Double unitPrice,
		Double monthlyBudget
) {

	/**
	 * Backward-compatible constructor for callers that predate the EOM rate/budget fields; leaves
	 * them {@code null} so their behaviour is unchanged.
	 *
	 * @param tactic     display name of the tactic as it appears in the Media Plan
	 * @param lineItemId BigQuery line-item identifier whose export rows feed this tactic's charts
	 * @param tacticNum  1-based tactic slot number (1-7) used to position the tactic on the slide
	 */
	public LineItemMapping(String tactic, String lineItemId, Integer tacticNum) {
		this(tactic, lineItemId, tacticNum, null, null, null);
	}
}
