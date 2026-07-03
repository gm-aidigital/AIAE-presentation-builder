package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchOption;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchTactic;
import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService;
import com.aidigital.reportconstructor.service.reports.services.LineItemMeta;
import com.aidigital.reportconstructor.service.reports.services.MatchResult;
import com.aidigital.reportconstructor.service.reports.services.TacticSuggestion;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the "Level 1 Naming" column from a BigQuery export, derives the line item
 * ID from the 9th underscore-delimited segment, then maps tactic strings from a
 * Media Plan to those IDs via a tactic&rarr;expected-channel lookup table (the
 * "unique ID" rule: a tactic auto-matches only when its expected BQ Channel has
 * exactly one line item ID).
 *
 * <p>Tactic extraction uses a {@link #WHITELIST whitelist} of recognised tactic
 * names, a {@link #STOP_PHRASES stop-phrase} prefix filter and a cap of
 * {@value #MAX_TACTICS}. Tactics are emitted in Media-column order so the response
 * index (tactic number) lines up with the collector's tactic numbering.
 */
@Service
public class LineItemMatcherServiceImpl implements LineItemMatcherService {

	private final LineItemNamingHelper lineItemNaming;
	private final LineItemMatchAssistant matchAssistant;

	public LineItemMatcherServiceImpl(LineItemNamingHelper lineItemNaming, LineItemMatchAssistant matchAssistant) {
		this.lineItemNaming = lineItemNaming;
		this.matchAssistant = matchAssistant;
	}

	/**
	 * Cap on the number of tactics pulled from the Media column.
	 */
	private static final int MAX_TACTICS = 7;

	/**
	 * Prefix stop phrases that exclude a Media-column row from the tactic list
	 * (matched as a {@code startsWith} prefix).
	 */
	private static final List<String> STOP_PHRASES = List.of(
			"total", "grand total", "subtotal", "totals", "total media",
			"added value reports",
			"ai digital insights reporting",
			"brand safety & guard",
			"brand safety and guard",
			"eoc or qbr insights report",
			"eoc or qbr",
			"ai digital brand study",
			"foot traffic lift study",
			"3rd party brand study"
	);

	/**
	 * Recognised tactic names (lowercase). Only rows whose normalised Media value
	 * is in this set become tactics.
	 */
	private static final Set<String> WHITELIST = Set.of(
			"blended set ctv/ott", "blended set ctv ott",
			"programmatic display",
			"rich media (html 5)", "rich media html 5",
			"geofencing (display)", "geofencing display",
			"programmatic mobile display",
			"native display",
			"native video",
			"programmatic video",
			"programmatic audio",
			"blended programmatic audio",
			"ott precision reach",
			"programmatic ctv",
			"streaming tv",
			"ctv precision reach",
			"ctv select",
			"network select bundle ctv",
			"network exclusive",
			"live news",
			"live tv",
			"local ctv",
			"zip code targeted ctv",
			"100% live sports package (100% live and in-game inventory)",
			"100% live sports package",
			"any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)",
			"any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)",
			"any live sports package",
			"college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)",
			"college football live sport package",
			"amazon fire tv",
			"google tv",
			"netflix (up to 10 sec creative)",
			"netflix (up to 15 sec creative)",
			"netflix (up to 30 sec creative)",
			"netflix",
			"youtube skippable in-stream (cpm)",
			"youtube skippable in-stream",
			"youtube non-skippable in-stream",
			"youtube ctv skippable in-stream",
			"youtube ctv non-skippable in-stream",
			"youtube in-feed (ex. discovery)",
			"youtube in-feed",
			"youtube bumper ads",
			"youtube demand gen",
			"gdn specific",
			"youtube shorts",
			"youtube tv (up to 15 sec)",
			"youtube tv (up to 30 sec)",
			"mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)",
			"mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)",
			"meta (cpm)", "meta (cpc)",
			"facebook specific",
			"meta lead forms",
			"meta boosted posts",
			"instagram specific",
			"twitter",
			"linkedin (cpm)", "linkedin (cpc)",
			"tiktok (cpm)", "tiktok (cpc)",
			"tiktok spark ads (cpm)", "tiktok spark ads (cpc)",
			"tiktok search ads",
			"pinterest (cpm)", "pinterest (cpc)",
			"reddit (cpm)", "reddit (cpc)",
			"snapchat (cpm)",
			"bing",
			"performance max",
			"google sem",
			"demand gen",
			"dooh",
			"amazon display (amazon & publisher network)", "amazon display",
			"amazon video (amazon & publisher network)", "amazon video",
			"amazon audio (amazon & publisher network)", "amazon audio",
			"amazon podcast ads",
			"twitch",
			"amazon sponsored ads",
			"app (google uac)", "google uac",
			"apple search ads"
	);

	/**
	 * Tactic (lowercase) &rarr; expected BQ Channel value(s). The first channel
	 * with exactly one line item ID wins. Multiple entries mean a tactic can
	 * appear under any of those channels. Kept distinct from the collector's
	 * channel filter, whose values differ — e.g. geofencing.
	 */
	private static final Map<String, List<String>> TACTIC_CHANNEL_MAP = new LinkedHashMap<>();

	static {
		Map<String, List<String>> m = TACTIC_CHANNEL_MAP;
		// CTV
		m.put("programmatic ctv", List.of("CTV"));
		m.put("streaming tv", List.of("CTV"));
		m.put("ctv precision reach", List.of("CTV"));
		m.put("ctv select", List.of("CTV"));
		m.put("network select bundle ctv", List.of("CTV"));
		m.put("network exclusive", List.of("CTV"));
		m.put("live news", List.of("CTV"));
		m.put("live tv", List.of("CTV"));
		m.put("local ctv", List.of("CTV"));
		m.put("zip code targeted ctv", List.of("CTV"));
		m.put("amazon fire tv", List.of("CTV"));
		m.put("google tv", List.of("CTV"));
		m.put("netflix (up to 10 sec creative)", List.of("CTV"));
		m.put("netflix (up to 15 sec creative)", List.of("CTV"));
		m.put("netflix (up to 30 sec creative)", List.of("CTV"));
		// OTT
		m.put("ott precision reach", List.of("OTT"));
		// CTV/OTT
		m.put("blended set ctv/ott", List.of("CTV/OTT"));
		m.put("blended set ctv ott", List.of("CTV/OTT"));
		// CTV Live Sports
		m.put("100% live sports package (100% live and in-game inventory)", List.of("CTV Live Sports"));
		m.put("any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)", List.of(
				"CTV" +
				" Live Sports"));
		m.put("any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)", List.of(
				"CTV Live Sports"));
		m.put("college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				List.of("CTV Live Sports"));
		m.put("college football live sport package (up to 50% live sports inventory / up to 50% ancillary inventory)",
				List.of("CTV Live Sports"));
		// YouTube (all formats)
		m.put("youtube skippable in-stream", List.of("YouTube"));
		m.put("youtube skippable in-stream (cpm)", List.of("YouTube"));
		m.put("youtube non-skippable in-stream", List.of("YouTube"));
		m.put("youtube bumper ads", List.of("YouTube"));
		m.put("youtube in-feed (ex. discovery)", List.of("YouTube"));
		m.put("youtube in-feed", List.of("YouTube"));
		m.put("youtube demand gen", List.of("YouTube"));
		m.put("youtube shorts", List.of("YouTube"));
		m.put("youtube ctv skippable in-stream", List.of("YouTube"));
		m.put("youtube ctv non-skippable in-stream", List.of("YouTube"));
		m.put("youtube tv (up to 15 sec)", List.of("YouTube"));
		m.put("youtube tv (up to 30 sec)", List.of("YouTube"));
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)", List.of("YouTube"));
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", List.of("YouTube"));
		// Display
		m.put("programmatic display", List.of("Display"));
		m.put("geofencing (display)", List.of("Display"));
		m.put("geofencing display", List.of("Display"));
		m.put("programmatic mobile display", List.of("In-App Display"));
		m.put("native display", List.of("Native"));
		m.put("rich media (html 5)", List.of("Rich Media"));
		m.put("rich media html 5", List.of("Rich Media"));
		m.put("gdn specific", List.of("Display"));
		// Video
		m.put("programmatic video", List.of("Video"));
		m.put("native video", List.of("Native Video"));
		// Audio
		m.put("programmatic audio", List.of("Audio"));
		m.put("blended programmatic audio", List.of("Audio"));
		m.put("amazon podcast ads", List.of("Audio"));
		// Social
		m.put("meta (cpm)", List.of("Meta"));
		m.put("meta (cpc)", List.of("Meta"));
		m.put("meta lead forms", List.of("Meta"));
		m.put("meta boosted posts", List.of("Meta"));
		m.put("facebook specific", List.of("Meta"));
		m.put("instagram specific", List.of("Meta"));
		m.put("tiktok (cpm)", List.of("TikTok"));
		m.put("tiktok (cpc)", List.of("TikTok"));
		m.put("tiktok spark ads (cpm)", List.of("TikTok"));
		m.put("tiktok spark ads (cpc)", List.of("TikTok"));
		m.put("tiktok search ads", List.of("TikTok"));
		m.put("linkedin (cpm)", List.of("LinkedIn"));
		m.put("linkedin (cpc)", List.of("LinkedIn"));
		m.put("pinterest (cpm)", List.of("Pinterest"));
		m.put("pinterest (cpc)", List.of("Pinterest"));
		m.put("reddit (cpm)", List.of("Reddit"));
		m.put("reddit (cpc)", List.of("Reddit"));
		m.put("snapchat (cpm)", List.of("Snapchat"));
		m.put("twitter", List.of("Twitter"));
		// Search / SEM
		m.put("google sem", List.of("Google Search"));
		m.put("demand gen", List.of("Google Search"));
		m.put("performance max", List.of("Performance Max"));
		m.put("bing", List.of("Bing Search"));
		m.put("app (google uac)", List.of("Google App"));
		m.put("google uac", List.of("Google App"));
		m.put("apple search ads", List.of("Apple Search"));
		// Amazon
		m.put("amazon display (amazon & publisher network)", List.of("Amazon Display"));
		m.put("amazon display", List.of("Amazon Display"));
		m.put("amazon video (amazon & publisher network)", List.of("Amazon Video"));
		m.put("amazon video", List.of("Amazon Video"));
		m.put("amazon audio (amazon & publisher network)", List.of("Audio"));
		m.put("amazon sponsored ads", List.of("Amazon Search"));
		m.put("twitch", List.of("Amazon Video Twitch"));
		// Other
		m.put("dooh", List.of("DOOH"));
	}

	@Override
	public MatchResult match(List<List<String>> bqRows, List<List<String>> planRows) {
		if (bqRows == null || bqRows.isEmpty()) {
			throw new AppException(ErrorReason.C002, "BQ rows are required");
		}

		List<String> bqHeaders = bqRows.get(0);
		int l1ColIdx = indexOfHeader(bqHeaders, h -> h.toLowerCase(Locale.ROOT).contains("level 1 naming"));
		if (l1ColIdx < 0) {
			throw new AppException(ErrorReason.C002,
					"Column 'Level 1 Naming' not found in BigQuery export");
		}
		int tacticColIdx = indexOfHeader(bqHeaders, h -> h.toLowerCase(Locale.ROOT).equals("tactic"));
		int channelColIdx = indexOfHeader(bqHeaders, h -> h.toLowerCase(Locale.ROOT).contains("channel"));

		// Line item metadata (first occurrence wins) + Channel -> [ids] lookup
		// (exact case, dedup, first-seen order) — both in a single pass over BQ.
		Map<String, LineItemMeta> byId = new LinkedHashMap<>();
		Map<String, List<String>> channelToIds = new LinkedHashMap<>();
		for (int i = 1; i < bqRows.size(); i++) {
			List<String> row = bqRows.get(i);
			String naming = cell(row, l1ColIdx).trim();
			if (naming.isEmpty()) {
				continue;
			}
			String id = extractLineItemId(naming);
			if (id == null) {
				continue;
			}

			String channel = cell(row, channelColIdx).trim();
			byId.computeIfAbsent(id, key -> new LineItemMeta(
					key, naming, channel, cell(row, tacticColIdx).trim()));

			if (!channel.isEmpty()) {
				List<String> ids = channelToIds.computeIfAbsent(channel, k -> new ArrayList<>());
				if (!ids.contains(id)) {
					ids.add(id);
				}
			}
		}

		// IDs are pure digits, so order numerically.
		List<String> uniqueIds = byId.keySet().stream()
				.sorted(java.util.Comparator.comparing(BigInteger::new))
				.toList();
		List<LineItemMeta> lineItems = uniqueIds.stream().map(byId::get).toList();

		List<PlanTactic> tactics = extractTacticRows(planRows);
		int size = tactics.size();
		List<String> matchedIds = new ArrayList<>(Collections.nCopies(size, ""));
		List<String> confidences = new ArrayList<>(Collections.nCopies(size, "none"));

		// Pass 1 — deterministic unique-ID rule: a tactic auto-matches when its expected
		// channel holds exactly one line item ID.
		for (int i = 0; i < size; i++) {
			List<String> channels = tacticChannels(tactics.get(i).name());
			if (channels == null) {
				continue;
			}
			for (String ch : channels) {
				List<String> ids = channelToIds.getOrDefault(ch, List.of());
				if (ids.size() == 1) {
					matchedIds.set(i, ids.get(0));
					confidences.set(i, "auto");
					break;
				}
			}
		}

		// Pass 2 — AI disambiguation for channels the unique-ID rule left ambiguous
		// (several tactics + several IDs sharing a channel).
		applyAiDisambiguation(tactics, matchedIds, confidences, channelToIds, byId);

		List<TacticSuggestion> suggestions = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			suggestions.add(new TacticSuggestion(tactics.get(i).name(), matchedIds.get(i), confidences.get(i)));
		}
		return new MatchResult(suggestions, lineItems, uniqueIds);
	}

	/**
	 * Runs the AI matcher over tactics still unmatched after the unique-ID pass whose expected channel
	 * holds more than one line item ID, then applies each returned assignment defensively: the ID must
	 * be unassigned and belong to the tactic's expected channel. Mutates {@code matchedIds} and
	 * {@code confidences} in place; a no-op when nothing is ambiguous or the assistant is stubbed.
	 *
	 * @param tactics      the extracted tactics (parallel to the id/confidence lists)
	 * @param matchedIds   per-tactic matched line item ID ("" when unmatched), mutated in place
	 * @param confidences  per-tactic confidence ("none"/"auto"), mutated in place
	 * @param channelToIds channel &rarr; its distinct line item IDs
	 * @param byId         line item ID &rarr; its metadata (used for the channel guard)
	 */
	void applyAiDisambiguation(
			List<PlanTactic> tactics,
			List<String> matchedIds,
			List<String> confidences,
			Map<String, List<String>> channelToIds,
			Map<String, LineItemMeta> byId) {

		Set<String> assigned = new HashSet<>(matchedIds);
		assigned.remove("");

		List<LineItemMatchTactic> ambiguous = new ArrayList<>();
		Set<String> wantedChannels = new HashSet<>();
		for (int i = 0; i < tactics.size(); i++) {
			if (!matchedIds.get(i).isEmpty()) {
				continue;
			}
			List<String> channels = tacticChannels(tactics.get(i).name());
			if (channels == null) {
				continue;
			}
			boolean multi = channels.stream().anyMatch(ch -> channelToIds.getOrDefault(ch, List.of()).size() > 1);
			if (!multi) {
				continue;
			}
			String channel = channels.get(0);
			wantedChannels.add(channel);
			ambiguous.add(new LineItemMatchTactic(i + 1, tactics.get(i).name(), channel, tactics.get(i).context()));
		}
		if (ambiguous.isEmpty()) {
			return;
		}

		List<LineItemMatchOption> options = new ArrayList<>();
		for (String channel : wantedChannels) {
			for (String id : channelToIds.getOrDefault(channel, List.of())) {
				if (assigned.contains(id)) {
					continue;
				}
				LineItemMeta meta = byId.get(id);
				options.add(new LineItemMatchOption(id, channel, meta == null ? "" : meta.naming()));
			}
		}
		if (options.isEmpty()) {
			return;
		}

		Map<Integer, String> picks = matchAssistant.match(ambiguous, options);
		for (Map.Entry<Integer, String> pick : picks.entrySet()) {
			int idx = pick.getKey() - 1;
			String id = pick.getValue();
			if (idx < 0 || idx >= tactics.size() || !matchedIds.get(idx).isEmpty() || assigned.contains(id)) {
				continue;
			}
			List<String> channels = tacticChannels(tactics.get(idx).name());
			LineItemMeta meta = byId.get(id);
			if (channels == null || meta == null || !channels.contains(meta.channel())) {
				continue;
			}
			matchedIds.set(idx, id);
			confidences.set(idx, "auto");
			assigned.add(id);
		}
	}

	/**
	 * Looks up the expected BQ channel(s) for a Media-Plan tactic name.
	 *
	 * @param tacticName the tactic name in original casing
	 * @return the expected channel list, or {@code null} when the tactic is unknown
	 */
	List<String> tacticChannels(String tacticName) {
		return TACTIC_CHANNEL_MAP.get(tacticName.trim().toLowerCase(Locale.ROOT));
	}

	String extractLineItemId(String naming) {
		return lineItemNaming.extractLineItemId(naming);
	}

	List<String> extractTactics(List<List<String>> planRows) {

		return extractTacticRows(planRows).stream().map(PlanTactic::name).toList();
	}

	/**
	 * Extracts whitelisted tactics from the Media column together with the context used to
	 * disambiguate duplicate tactic names: the most recent section/group label plus the tactic
	 * row's own cells (comments, targeting, goal…). Same order, whitelist, stop-phrase filter and
	 * {@link #MAX_TACTICS} cap as {@link #extractTactics}.
	 *
	 * @param planRows the Media Plan grid (may be null/empty)
	 * @return the tactics with context, in Media-column order
	 */
	List<PlanTactic> extractTacticRows(List<List<String>> planRows) {

		List<PlanTactic> tactics = new ArrayList<>();
		if (planRows == null || planRows.isEmpty()) {
			return tactics;
		}

		int mediaRow = -1;
		int mediaCol = -1;
		outer:
		for (int i = 0; i < planRows.size(); i++) {
			List<String> row = planRows.get(i);
			for (int j = 0; j < row.size(); j++) {
				if (cell(row, j).trim().equalsIgnoreCase("media")) {
					mediaRow = i;
					mediaCol = j;
					break outer;
				}
			}
		}
		if (mediaRow < 0) {
			return tactics;
		}

		String group = "";
		for (int i = mediaRow + 1; i < planRows.size(); i++) {
			List<String> row = planRows.get(i);
			String value = cell(row, mediaCol).trim();
			if (value.isEmpty()) {
				// Section-label rows (e.g. "Grapevine Vintage Railroad") have an empty Media cell but
				// a label elsewhere; remember it as the group for the tactics beneath it.
				String label = firstNonEmpty(row);
				if (!label.isEmpty()) {
					group = label;
				}
				continue;
			}
			String lower = value.toLowerCase(Locale.ROOT);
			// Stop-phrase prefix filter.
			boolean stop = STOP_PHRASES.stream().anyMatch(lower::startsWith);
			if (stop) {
				continue;
			}
			// Whitelist: only recognised tactic names pass.
			if (!WHITELIST.contains(lower)) {
				continue;
			}
			tactics.add(new PlanTactic(value, buildContext(group, row, mediaCol)));
			if (tactics.size() >= MAX_TACTICS) {
				break;
			}
		}
		return tactics;
	}

	/**
	 * Joins the current group label with the tactic row's other non-empty cells into a single
	 * context string (Media cell excluded, whitespace collapsed).
	 *
	 * @param group    the most recent section/group label ("" when none)
	 * @param row      the tactic row
	 * @param mediaCol the Media column index to skip
	 * @return the joined context string
	 */
	String buildContext(String group, List<String> row, int mediaCol) {

		List<String> parts = new ArrayList<>();
		if (!group.isEmpty()) {
			parts.add(group);
		}
		for (int j = 0; j < row.size(); j++) {
			if (j == mediaCol) {
				continue;
			}
			String c = cell(row, j).trim();
			if (!c.isEmpty()) {
				parts.add(c);
			}
		}
		return String.join(" · ", parts).replaceAll("\\s+", " ").trim();
	}

	/**
	 * Returns the first non-empty, trimmed cell of a row, or "" when the row is empty/blank.
	 *
	 * @param row the row to scan
	 * @return the first non-empty cell value, or ""
	 */
	String firstNonEmpty(List<String> row) {

		if (row == null) {
			return "";
		}
		for (String c : row) {
			if (c != null && !c.trim().isEmpty()) {
				return c.trim();
			}
		}
		return "";
	}

	int indexOfHeader(List<String> headers, java.util.function.Predicate<String> match) {

		for (int i = 0; i < headers.size(); i++) {
			if (match.test(headers.get(i) == null ? "" : headers.get(i))) {
				return i;
			}
		}
		return -1;
	}

	String cell(List<String> row, int idx) {

		if (idx < 0 || row == null || idx >= row.size()) {
			return "";
		}
		return row.get(idx) == null ? "" : row.get(idx);
	}
}
