package com.aidigital.reportconstructor.domain.reports.entities;

import com.aidigital.reportconstructor.domain.common.entities.IdAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Persisted progress and outcome of a single report-generation run.
 * Public job id is the surrogate {@link #getId()} (int64).
 */
@Entity
@Table(name = "report_jobs")
@Getter
@Setter
public class ReportJobEntity extends IdAwareEntity {

	@Column(name = "owner_user_id", nullable = false)
	private String ownerUserId;

	@Column(name = "owner_email")
	private String ownerEmail;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "step", nullable = false)
	private Integer step;

	@Column(name = "total", nullable = false)
	private Integer total;

	@Column(name = "label")
	private String label;

	@Column(name = "report_type_code")
	private String reportTypeCode;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload_json", columnDefinition = "jsonb")
	private String payloadJson;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "warnings_json", columnDefinition = "jsonb")
	private String warningsJson;

	@Column(name = "slide_url")
	private String slideUrl;

	@Column(name = "sheet_url")
	private String sheetUrl;

	@Column(name = "artifact_name")
	private String artifactName;

	@Column(name = "target")
	private String target;

	@Column(name = "media_plan_url")
	private String mediaPlanUrl;

	@Column(name = "elevate_url")
	private String elevateUrl;

	@Column(name = "error_message")
	private String errorMessage;

	/** Plain (uncached) input tokens Claude billed across the whole run; null for pre-accounting jobs. */
	@Column(name = "input_tokens")
	private Long inputTokens;

	/** Output tokens Claude generated across the whole run; null for pre-accounting jobs. */
	@Column(name = "output_tokens")
	private Long outputTokens;

	/** Input tokens written into the prompt cache, billed above the plain input rate. */
	@Column(name = "cache_write_tokens")
	private Long cacheWriteTokens;

	/** Input tokens served from the prompt cache, billed well below the plain input rate. */
	@Column(name = "cache_read_tokens")
	private Long cacheReadTokens;

	/** Number of Anthropic Messages API calls the run made. */
	@Column(name = "claude_calls")
	private Integer claudeCalls;

	/** Claude model the run billed against, so cost stays attributable after the configured model changes. */
	@Column(name = "claude_model")
	private String claudeModel;

	/**
	 * Every token the run consumed, summed by the database from the four columns above.
	 *
	 * <p>Read-only here because the column is {@code GENERATED ALWAYS} — it exists so "order the
	 * reports by token spend" is an indexed {@code ORDER BY} rather than a sort the server performs
	 * after fetching every row. Writing to it is not merely unnecessary, it is rejected by Postgres.
	 */
	@Column(name = "total_tokens", insertable = false, updatable = false)
	private Long totalTokens;

	/**
	 * Slides the finished deck shipped with, counted after the surplus template slides were deleted.
	 * Drives the saved-hours figure, whose manual baseline is quoted per slide. Null for a sheet-only
	 * run, for a run that failed before the deck existed, and for jobs older than this column — all of
	 * which fall back to the configured per-report-type slide default rather than counting as zero.
	 */
	@Column(name = "slide_count")
	private Integer slideCount;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
