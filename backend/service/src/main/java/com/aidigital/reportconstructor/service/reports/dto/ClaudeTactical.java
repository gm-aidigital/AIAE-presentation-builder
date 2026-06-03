package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;

/**
 * Structured output of Claude Batch B (tactical) — port of the per-tactic
 * {@code male / female / weekdays / weekends} JSON contract in PHP
 * {@code claude_api.php}. Keyed by 1-based tactic number.
 */
public record ClaudeTactical(Map<Integer, TacticInsight> byTactic) {

    public record TacticInsight(int male, int female, String weekdays, String weekends) {}

    public static ClaudeTactical empty() {
        return new ClaudeTactical(Map.of());
    }

    public TacticInsight get(int n) {
        return byTactic == null ? null : byTactic.get(n);
    }
}
