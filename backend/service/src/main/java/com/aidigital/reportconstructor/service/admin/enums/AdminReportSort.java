package com.aidigital.reportconstructor.service.admin.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Columns the team-wide report table can be ordered by.
 *
 * <p>An enum rather than a free-text parameter because the value ends up in an {@code ORDER BY}: a
 * client that could name any property could sort by — and so probe — columns the table never shows.
 * Each constant carries the entity property it maps to, so the wire code and the schema can be
 * renamed independently.
 *
 * <p>Cost is deliberately absent. It is computed at read time from the configured list prices, so no
 * column exists for the database to order by; the closest honest server-side answer is
 * {@link #TOKENS}, which the generated {@code total_tokens} column makes an indexed sort.
 */
@Getter
@RequiredArgsConstructor
public enum AdminReportSort {

	/** When the report was started. The default, and the order the table's index is built for. */
	CREATED_AT("createdAt", "createdAt"),

	/** Every token the run consumed, from the generated {@code total_tokens} column. */
	TOKENS("tokens", "totalTokens"),

	/** Slides the finished deck shipped with. */
	SLIDES("slides", "slideCount"),

	/** Owner email, alphabetically. */
	OWNER("owner", "ownerEmail"),

	/** Report type code. */
	TYPE("type", "reportTypeCode"),

	/** Job status. */
	STATUS("status", "status");

	/** Wire code clients send. */
	private final String code;

	/** Entity property this column orders by. */
	private final String property;
}
