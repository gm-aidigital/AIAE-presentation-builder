package com.aidigital.reportconstructor.domain.reports.entities;

import com.aidigital.reportconstructor.domain.common.entities.IdAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * One Anthropic Messages API call's token consumption.
 *
 * <p>Finer-grained than the totals on {@link ReportJobEntity}, which cannot express a call made
 * outside any report or a billed call whose reply never arrived — see {@link #getStatus()}.
 */
@Entity
@Table(name = "claude_usage_events")
@Getter
@Setter
public class ClaudeUsageEventEntity extends IdAwareEntity {

	/** Report job the call belongs to, or {@code null} for calls made outside a job. */
	@Column(name = "job_id")
	private Long jobId;

	@Column(name = "owner_user_id")
	private String ownerUserId;

	@Column(name = "owner_email")
	private String ownerEmail;

	/** Batch tag the call was logged under, e.g. {@code BatchC}, {@code BatchGeo}, {@code LineItemMatch}. */
	@Column(name = "label", nullable = false)
	private String label;

	@Column(name = "model")
	private String model;

	/**
	 * Wire code of the usage status: {@code recorded} when the token counts come from the API's own
	 * usage block, {@code estimated} when the call was billed but its reply never arrived and the
	 * counts are a local estimate.
	 */
	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "input_tokens", nullable = false)
	private long inputTokens;

	@Column(name = "output_tokens", nullable = false)
	private long outputTokens;

	@Column(name = "cache_write_tokens", nullable = false)
	private long cacheWriteTokens;

	@Column(name = "cache_read_tokens", nullable = false)
	private long cacheReadTokens;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
