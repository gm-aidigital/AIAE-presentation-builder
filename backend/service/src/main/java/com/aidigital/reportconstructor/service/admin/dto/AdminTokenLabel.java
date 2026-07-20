package com.aidigital.reportconstructor.service.admin.dto;

/**
 * Measured Claude spend for one pipeline stage, keyed by the batch tag its calls are logged under
 * ({@code BatchC}, {@code BatchGeo}, {@code LineItemMatch}, …). This is what the per-call event
 * table buys over the per-job totals: it says which stage the money goes to.
 *
 * @param label        the batch tag
 * @param calls        measured calls the stage made
 * @param totalTokens  measured tokens the stage consumed
 * @param costUsd      estimated cost of those tokens
 * @param unknownCalls calls of this stage that were billed but whose reply never arrived
 */
public record AdminTokenLabel(String label, long calls, long totalTokens, double costUsd, long unknownCalls) {
}
