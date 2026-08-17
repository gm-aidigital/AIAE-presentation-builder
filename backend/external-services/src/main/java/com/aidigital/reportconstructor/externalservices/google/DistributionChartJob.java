package com.aidigital.reportconstructor.externalservices.google;

/**
 * Inputs for one distribution (pie) chart.
 *
 * @param tacticNum   one-based tactic number (used in log messages)
 * @param templateId  source chart-template spreadsheet id
 * @param target      the placeholder chart this one replaces
 * @param copyName    name for the copied spreadsheet
 * @param tacticName  display name of the tactic
 * @param tacticImp   this tactic's impressions
 * @param otherImps   impressions for the "Other" slice, i.e. total impressions minus this tactic's impressions
 */
record DistributionChartJob(
		int tacticNum,
		String templateId,
		ChartTarget target,
		String copyName,
		String tacticName,
		double tacticImp,
		double otherImps) {

}
