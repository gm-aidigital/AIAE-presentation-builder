package com.aidigital.reportconstructor.domain.reports.projections;

import java.time.OffsetDateTime;

/**
 * One report owner's internal id paired with the email last recorded for it.
 *
 * <p>The {@code usage_daily} rollup keys on the internal id alone, because an id is stable and an
 * email is not. This projection is how the dashboard puts a name back on those rows without reading
 * a single job: one grouped row per user, joined to the rollup in memory.
 *
 * <p>{@code lastActivity} rides along because it is the one per-user figure the rollup would answer
 * imprecisely: the rollup's grain is a day, so it could only ever say "that Tuesday", while the jobs
 * table still holds the moment.
 *
 * @param ownerUserId  internal owner id
 * @param ownerEmail   the most recent email recorded for that owner, or {@code null} for legacy rows
 * @param lastActivity when that owner most recently started a report
 */
public record JobOwner(String ownerUserId, String ownerEmail, OffsetDateTime lastActivity) {
}
