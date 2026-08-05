package com.aidigital.reportconstructor.domain.reports.entities;

import com.aidigital.reportconstructor.domain.common.entities.IdAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One day of aggregated report activity for a single (user, report type, target, model) combination.
 *
 * <p>A read cache over {@link ReportJobEntity}, not a source of truth: every row is recomputed from
 * the jobs it summarises, so a wrong row is fixed by rebuilding its day rather than by editing it.
 * It exists because the admin dashboard's month, week and active-user series would otherwise have
 * to read every job row — JSONB payloads included — on each request.
 *
 * <p>Deliberately stores no cost column. Cost is derived at read time from the token counts and the
 * configured list prices for {@link #getClaudeModel()}, so a price change re-prices history instead
 * of requiring a rebuild.
 */
@Entity
@Table(name = "usage_daily")
@Getter
@Setter
public class UsageDailyEntity extends IdAwareEntity {

	/** Calendar day the summarised jobs were created on, in the server's zone. */
	@Column(name = "day", nullable = false)
	private LocalDate day;

	@Column(name = "owner_user_id", nullable = false)
	private String ownerUserId;

	/** Report type code, uppercased; {@code OTHER} when the job carried none. */
	@Column(name = "report_type_code", nullable = false)
	private String reportTypeCode;

	/** Generation target, e.g. {@code SLIDES}/{@code SHEET}; {@code UNKNOWN} when the job carried none. */
	@Column(name = "target", nullable = false)
	private String target;

	/** Claude model these jobs billed against; {@code UNKNOWN} when they made no call. */
	@Column(name = "claude_model", nullable = false)
	private String claudeModel;

	/** Jobs created that day in this combination, whatever their outcome. */
	@Column(name = "jobs", nullable = false)
	private int jobs;

	/** Of those, the ones carrying recorded token counts — the denominator for per-report averages. */
	@Column(name = "jobs_with_usage", nullable = false)
	private int jobsWithUsage;

	@Column(name = "failed_jobs", nullable = false)
	private int failedJobs;

	@Column(name = "claude_calls", nullable = false)
	private long claudeCalls;

	@Column(name = "input_tokens", nullable = false)
	private long inputTokens;

	@Column(name = "output_tokens", nullable = false)
	private long outputTokens;

	@Column(name = "cache_write_tokens", nullable = false)
	private long cacheWriteTokens;

	@Column(name = "cache_read_tokens", nullable = false)
	private long cacheReadTokens;

	/** Slides the finished decks shipped with, summed. */
	@Column(name = "slides", nullable = false)
	private long slides;

	/** Of those jobs, the ones that produced a measured deck — the denominator for slides per report. */
	@Column(name = "jobs_with_slides", nullable = false)
	private int jobsWithSlides;

	/** Wall-clock seconds those runs took, summed, for the time the automation actually cost. */
	@Column(name = "generation_seconds", nullable = false)
	private long generationSeconds;

	@Column(name = "refreshed_at", nullable = false)
	private OffsetDateTime refreshedAt;
}
