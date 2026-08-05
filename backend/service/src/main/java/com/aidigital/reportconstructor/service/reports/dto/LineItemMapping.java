package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Links one media-plan tactic to its BigQuery line item so chart data can be queried per tactic,
 * plus the EOM-only rate/budget economics entered by the user for that tactic while matching.
 *
 * @param tactic        display name of the tactic as it appears in the Media Plan
 * @param lineItemId    BigQuery line-item identifier whose export rows feed this tactic's charts
 * @param tacticNum     1-based tactic slot number used to position the tactic on the slide
 * @param rateType      EOM-only: how this tactic's cost is bought (CPM/CPC/CPV); {@code null} for EOC
 * @param unitPrice     EOM-only: the final unit price entered by the user for {@code rateType}; {@code null} for EOC
 * @param monthlyBudget EOM-only: the monthly budget entered by the user for this tactic; {@code null} for EOC
 * @param planTacticNum 1-based position of this tactic in the media plan's Media column, before the user
 *                      excluded any rows at matching time; {@code null} means "same as {@code tacticNum}".
 *                      Excluding a row renumbers the survivors 1..N in {@code tacticNum}, so this is the only
 *                      field that still points at the original plan row the Estimates figures line up with.
 */
public record LineItemMapping(
		String tactic,
		String lineItemId,
		Integer tacticNum,
		RateType rateType,
		Double unitPrice,
		Double monthlyBudget,
		Integer planTacticNum
) {

	/**
	 * Backward-compatible constructor for callers that predate the EOM rate/budget fields; leaves
	 * them {@code null} so their behaviour is unchanged.
	 *
	 * @param tactic     display name of the tactic as it appears in the Media Plan
	 * @param lineItemId BigQuery line-item identifier whose export rows feed this tactic's charts
	 * @param tacticNum  1-based tactic slot number used to position the tactic on the slide
	 */
	public LineItemMapping(String tactic, String lineItemId, Integer tacticNum) {
		this(tactic, lineItemId, tacticNum, null, null, null, null);
	}

	/**
	 * Backward-compatible constructor for callers that predate row exclusion; the tactic keeps its
	 * media-plan position, so the plan number mirrors the slot number.
	 *
	 * @param tactic        display name of the tactic as it appears in the Media Plan
	 * @param lineItemId    BigQuery line-item identifier whose export rows feed this tactic's charts
	 * @param tacticNum     1-based tactic slot number used to position the tactic on the slide
	 * @param rateType      EOM-only buy type; {@code null} for EOC
	 * @param unitPrice     EOM-only unit price; {@code null} for EOC
	 * @param monthlyBudget EOM-only monthly budget; {@code null} for EOC
	 */
	public LineItemMapping(String tactic, String lineItemId, Integer tacticNum, RateType rateType,
	                       Double unitPrice, Double monthlyBudget) {
		this(tactic, lineItemId, tacticNum, rateType, unitPrice, monthlyBudget, null);
	}

	/**
	 * Returns the media-plan position this tactic came from, falling back to the slot number for
	 * payloads written before row exclusion existed.
	 *
	 * @return the 1-based media-plan position, or {@code null} when neither number is set
	 */
	public Integer planNumOrSlot() {
		return planTacticNum != null ? planTacticNum : tacticNum;
	}
}
