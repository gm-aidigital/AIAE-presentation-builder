package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One observation → action → expected impact chain of the EOM deck's "What we did this month & why"
 * slide, filling the six tokens of one column ({@code {{observation N}}} / {@code {{observation N text}}}
 * and the matching {@code action} / {@code impact} pairs).
 *
 * <p>The three links are one record rather than three independent lists because the slide reads down the
 * column, not across the row: the action must answer the observation directly above it and the impact must
 * be the consequence of that action. Kept apart, nothing would hold the third column's impact to the third
 * column's observation.
 *
 * <p>Each heading is the label printed above its paragraph and is written to a much shorter budget than the
 * paragraph it heads. A {@code null} field means the model declined that slot, and the token renders as a
 * dash rather than as stale copy.
 *
 * @param observation     heading of the signal the analytics surfaced ({@code {{observation N}}})
 * @param observationText the signal itself — the fact, anomaly or outlier ({@code {{observation N text}}})
 * @param action          heading of the team's tactical response ({@code {{action N}}})
 * @param actionText      what we actually changed in response to that signal ({@code {{action N text}}})
 * @param impact          heading of the business outcome the change is bought for ({@code {{impact N}}})
 * @param impactText      the outcome expected from that change ({@code {{impact N text}}})
 */
public record WhatWeDidStep(
		String observation,
		String observationText,
		String action,
		String actionText,
		String impact,
		String impactText
) {
}
