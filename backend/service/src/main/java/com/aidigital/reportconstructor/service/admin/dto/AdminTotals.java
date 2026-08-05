package com.aidigital.reportconstructor.service.admin.dto;

/**
 * The dashboard's headline counters.
 *
 * <p>Split by what they are about, not by what they look like. {@code reports}, {@code activeUsers}
 * and {@code newUsers} describe the selected window and move when the date range moves.
 * {@code running} and {@code failed} deliberately do not: a job queued right now is not a fact about
 * July, and scoping it to a past window would report zero while work was in flight.
 *
 * @param reports     reports created in the window
 * @param activeUsers distinct users who created one, counted once each
 * @param newUsers    of those, the ones who had never created a report before the window
 * @param running     jobs queued or running right now, regardless of the window
 * @param failed      jobs that ended in error, regardless of the window
 */
public record AdminTotals(
		int reports,
		int activeUsers,
		int newUsers,
		int running,
		int failed) {
}
