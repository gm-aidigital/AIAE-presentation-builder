package com.aidigital.reportconstructor.service.admin.dto;

/**
 * What the generated reports would have cost to build by hand, and what that is worth.
 *
 * <p>Every figure here is a modelled counterfactual, not a measurement, and the record is shaped to
 * say so: the two assumptions it rests on — {@code hourlyRateUsd} and {@code minutesPerSlide} —
 * travel with the answer so a reader can re-derive it or disagree with it, instead of being handed a
 * dollar amount with no visible basis.
 *
 * @param reportsCounted         reports the figure covers
 * @param slidesTotal            slides those reports shipped, measured where known and modelled where not
 * @param slidesMeasured         of those, the slides that were actually counted in a finished deck
 * @param avgSlidesPerReport     mean slides per report
 * @param manualHours            hours the same output would have cost by hand
 * @param automationHours        hours the generated runs actually took, wall-clock
 * @param savedHours             {@code manualHours} less {@code automationHours}, floored at zero
 * @param savedUsd               {@code savedHours} valued at {@code hourlyRateUsd}
 * @param hourlyRateUsd          the loaded hourly rate the saving is valued at
 * @param minutesPerSlide        the manual baseline one slide is assumed to cost
 */
public record AdminSavings(
		int reportsCounted,
		long slidesTotal,
		long slidesMeasured,
		double avgSlidesPerReport,
		double manualHours,
		double automationHours,
		double savedHours,
		double savedUsd,
		double hourlyRateUsd,
		int minutesPerSlide) {
}
