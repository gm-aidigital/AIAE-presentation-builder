package com.aidigital.reportconstructor.externalservices.anthropic;

import java.util.List;

/**
 * Outcome of one attempt at a per-section Claude call: either the section's strings, or the reason the reply
 * was turned down.
 *
 * <p>The reason is what makes a retry worth sending. A retry that repeats the original prompt byte for byte
 * reproduces a deterministic rejection — the same three-item array, the same prose wrapper — so the next
 * attempt is told what was wrong with the last one, and the reason travels back from the attempt for exactly
 * that purpose.
 *
 * @param values    the accepted strings in slide order, or an empty list when the reply was rejected
 * @param rejection what was wrong with the reply, in words that survive being shown to a user; {@code null}
 *                  when {@code values} carries the accepted strings
 */
public record ClaudeSectionAttempt(List<String> values, String rejection) {

}
