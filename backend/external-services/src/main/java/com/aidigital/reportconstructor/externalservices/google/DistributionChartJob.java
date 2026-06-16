package com.aidigital.reportconstructor.externalservices.google;

/**
 * Inputs for one distribution (pie) chart.
 *
 * @param tacticNum   one-based tactic number (used in log messages)
 * @param templateId  source chart-template spreadsheet id
 * @param oldObjectId slide object id of the placeholder chart to replace
 * @param copyName    name for the copied spreadsheet
 * @param tacticName  display name of the tactic
 * @param tacticImp   this tactic's impressions
 * @param otherImps   total impressions across all tactics
 */
record DistributionChartJob(
		int tacticNum,
		String templateId,
		String oldObjectId,
		String copyName,
		String tacticName,
		double tacticImp,
		double otherImps) {

}
