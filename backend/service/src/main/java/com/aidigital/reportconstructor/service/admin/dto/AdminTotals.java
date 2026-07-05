package com.aidigital.reportconstructor.service.admin.dto;

/**
 * Headline counters for the admin dashboard's stat cards.
 *
 * @param reportsTotal all report jobs ever created
 * @param thisMonth    jobs created in the current calendar month
 * @param activeUsers  distinct users who created at least one report
 * @param running      jobs currently queued or running (technical health)
 * @param failed       jobs that ended in error (technical health)
 */
public record AdminTotals(
		int reportsTotal,
		int thisMonth,
		int activeUsers,
		int running,
		int failed) {
}
