package com.aidigital.reportconstructor.service.reports.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How trustworthy one Claude call's recorded token counts are.
 *
 * <p>The distinction exists so a call whose reply never arrived cannot quietly turn the whole
 * spend figure into a guess: {@link #RECORDED} tokens are reported as fact and {@link #ESTIMATED}
 * ones are shown beside them as a prediction.
 */
@Getter
@RequiredArgsConstructor
public enum ClaudeUsageStatus {

	/** Counts came from the API's own {@code usage} block. */
	RECORDED("recorded"),

	/**
	 * The call was sent and billed, but no reply arrived — a timeout or a dropped connection — so the
	 * input side is a local estimate of the prompt and the output side is unknown.
	 */
	ESTIMATED("estimated");

	/** Wire code persisted in {@code claude_usage_events.status}. */
	private final String code;
}
