package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One tactic paired with the funnel goal the media plan gave it, as the north-star slide's channel lists
 * read them.
 *
 * <p>Both fields are the already-resolved values — the tactic name exactly as {@code {{tactic N}}} prints
 * it and the goal exactly as the plan's "Goal" column spells it — so the lists name channels the same way
 * every table after them does.
 *
 * @param number one-based tactic index, kept so the lists follow media-plan order
 * @param name   the tactic's display name ({@code null} when the slot resolved to nothing)
 * @param goal   the tactic's funnel goal from the media plan ({@code null} when the plan carries none)
 */
public record TacticFunnelEntry(int number, String name, String goal) {

}
