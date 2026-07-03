package com.aidigital.reportconstructor.service.reports.dto;

/**
 * A tactic row pulled from the Media column together with the surrounding context used
 * to disambiguate duplicate tactic names.
 *
 * @param name    the tactic name as it appears in the Media column (original casing)
 * @param context section/group label plus the row's other cells (comments, targeting, goal…), joined
 */
public record PlanTactic(String name, String context) {

}
