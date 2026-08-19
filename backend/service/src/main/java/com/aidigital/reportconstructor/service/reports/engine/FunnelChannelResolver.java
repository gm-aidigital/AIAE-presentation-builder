package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FunnelChannelGroup;
import com.aidigital.reportconstructor.service.reports.dto.TacticFunnelEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the north-star slide's three channel lists — the tactics the media plan marked as awareness, as
 * consideration, and as conversions.
 *
 * <p>Read straight from the plan's "Goal" column, the same column {@code {{tactic N goal}}} already reads,
 * so the lists are a regrouping of values the user can see per tactic rather than a second interpretation
 * of them. They are resolved with the rest of the campaign placeholders, which is what puts them in the
 * generated workbook's Info block — from there the slides step reads them back like any other Info value,
 * so an edit in the sheet reaches the deck.
 */
@Component
@RequiredArgsConstructor
public class FunnelChannelResolver {

	/** Separator between channel names inside one stage's list. */
	static final String SEPARATOR = ", ";

	private final CampaignResolvers campaignResolvers;

	/**
	 * Resolves all three channel-list placeholders, preferring a hand-entered value per stage.
	 *
	 * <p>Names keep media-plan order and are de-duplicated, so a channel bought on several plan lines is
	 * named once. A tactic whose plan row names two stages is listed under both — that is what the plan says
	 * the line was bought for. A stage no tactic was marked for resolves to nothing, which the deck and the
	 * sheet both render as an em dash.
	 *
	 * @param sheetRows Media Plan tab rows
	 * @param adjRows   manual Adjustments tab rows (checked first)
	 * @param tactics   the campaign's tactics paired with their plan goals, in media-plan order
	 * @return a map keyed by placeholder ({@code {{awareness/consideration/conversions channels}}}) to its
	 * {@link Resolved}; values may be {@code "not_found"}
	 */
	public Map<String, Resolved> resolveFunnelChannels(
			List<List<String>> sheetRows, List<List<String>> adjRows, List<TacticFunnelEntry> tactics) {

		Map<String, Resolved> result = new LinkedHashMap<>();
		for (FunnelChannelGroup group : FunnelChannelGroup.values()) {
			String label = group.label();
			Resolved manual = campaignResolvers.resolve(sheetRows, adjRows, label);
			if (manual.found()) {
				result.put(group.token(), manual);
				continue;
			}
			List<String> names = namesFor(group, tactics);
			result.put(group.token(), names.isEmpty()
					? new Resolved(label, null, "not_found")
					: new Resolved(label + " (auto: media plan goal column)",
							String.join(SEPARATOR, names), "auto"));
		}
		return result;
	}

	/**
	 * Collects the tactic names one funnel stage covers, in media-plan order and without repeats.
	 *
	 * @param group   the funnel stage being filled
	 * @param tactics the campaign's tactics paired with their plan goals
	 * @return the stage's channel names, empty when no tactic was marked for it
	 */
	List<String> namesFor(FunnelChannelGroup group, List<TacticFunnelEntry> tactics) {
		List<String> names = new ArrayList<>();
		for (TacticFunnelEntry tactic : tactics) {
			if (tactic == null || tactic.name() == null || tactic.goal() == null) {
				continue;
			}
			String name = tactic.name().trim();
			if (name.isEmpty() || names.contains(name)) {
				continue;
			}
			if (group.matches(tactic.goal().trim().toLowerCase(Locale.ROOT))) {
				names.add(name);
			}
		}
		return names;
	}
}
