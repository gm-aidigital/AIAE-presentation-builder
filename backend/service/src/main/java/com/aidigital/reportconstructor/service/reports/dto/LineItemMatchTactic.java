package com.aidigital.reportconstructor.service.reports.dto;

/**
 * A media-plan tactic offered to the AI matcher for line-item disambiguation.
 *
 * @param tacticNum 1-based tactic number, matching the order tactics are emitted from the Media column
 * @param name      the tactic name as it appears in the Media Plan (original casing)
 * @param channel   the expected BigQuery Channel the tactic belongs to (assignments never cross channels)
 * @param context   free-text context (section/group label, comments, targeting, goal…) that distinguishes
 *                  otherwise identically named tactics
 */
public record LineItemMatchTactic(int tacticNum, String name, String channel, String context) {

}
