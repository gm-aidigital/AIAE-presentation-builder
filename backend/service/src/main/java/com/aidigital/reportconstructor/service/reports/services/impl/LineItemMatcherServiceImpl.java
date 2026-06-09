package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
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

    public LineItemMatcherServiceImpl(LineItemNamingHelper lineItemNaming) {
        this.lineItemNaming = lineItemNaming;
    }

    /** Cap on the number of tactics pulled from the Media column. */
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
        m.put("any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)", List.of("CTV Live Sports"));
        m.put("any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)", List.of("CTV Live Sports"));
        m.put("college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)", List.of("CTV Live Sports"));
        m.put("college football live sport package (up to 50% live sports inventory / up to 50% ancillary inventory)", List.of("CTV Live Sports"));
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

        List<String> tactics = extractTactics(planRows);
        List<TacticSuggestion> suggestions = new ArrayList<>();
        for (String tactic : tactics) {
            String normName = tactic.trim().toLowerCase(Locale.ROOT);
            List<String> channels = TACTIC_CHANNEL_MAP.get(normName);

            String matchedId = null;
            if (channels != null) {
                // Pick the first expected channel that has exactly one line item ID.
                for (String ch : channels) {
                    List<String> ids = channelToIds.getOrDefault(ch, List.of());
                    if (ids.size() == 1) {
                        matchedId = ids.get(0);
                        break;
                    }
                }
            }

            suggestions.add(new TacticSuggestion(
                tactic,
                matchedId == null ? "" : matchedId,
                matchedId == null ? "none" : "auto"));
        }
        return new MatchResult(suggestions, lineItems, uniqueIds);
    }

    String extractLineItemId(String naming) {
        return lineItemNaming.extractLineItemId(naming);
    }

    List<String> extractTactics(List<List<String>> planRows) {

        List<String> tactics = new ArrayList<>();
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

        for (int i = mediaRow + 1; i < planRows.size(); i++) {
            String value = cell(planRows.get(i), mediaCol).trim();
            if (value.isEmpty()) {
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
            tactics.add(value);
            if (tactics.size() >= MAX_TACTICS) {
                break;
            }
        }
        return tactics;
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
