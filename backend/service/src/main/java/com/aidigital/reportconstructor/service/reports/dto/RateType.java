package com.aidigital.reportconstructor.service.reports.dto;

/**
 * How a matched line item's cost is bought, chosen by the user per tactic while matching the
 * media plan against Elevate raw data. Drives which Plan Units figure (impressions/clicks/views)
 * {@link com.aidigital.reportconstructor.service.reports.engine.RatePlanCalculator} derives from
 * {@code unitPrice} and {@code monthlyBudget}.
 */
public enum RateType {

	/**
	 * Cost per 1000 impressions; Plan Units is planned impressions.
	 */
	CPM,

	/**
	 * Cost per click; Plan Units is planned clicks.
	 */
	CPC,

	/**
	 * Cost per view; Plan Units is planned views.
	 */
	CPV
}
