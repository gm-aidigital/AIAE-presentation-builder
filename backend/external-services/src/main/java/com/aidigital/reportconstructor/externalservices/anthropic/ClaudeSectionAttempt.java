package com.aidigital.reportconstructor.externalservices.anthropic;

import java.util.List;

/**
 * Outcome of one attempt at a per-section Claude call: the section's field values, plus the reason the reply
 * was short of complete when it was.
 *
 * <p>The two travel together on purpose. The reason is what makes a retry worth sending — a retry that repeats
 * the original prompt byte for byte reproduces a deterministic shortfall, so the next attempt is told what the
 * last one left out. The values travel back even when a reason is set, because a field the model did answer is
 * worth shipping whatever happened to its neighbours; the caller keeps the fullest attempt it saw.
 *
 * @param values    the field values in slide order, blank where the model never answered; an empty list when
 *                  the call failed or the reply carried none of the asked fields
 * @param rejection what was short of complete, in words that survive being shown to a user; {@code null} when
 *                  every field arrived
 */
public record ClaudeSectionAttempt(List<String> values, String rejection) {

}
