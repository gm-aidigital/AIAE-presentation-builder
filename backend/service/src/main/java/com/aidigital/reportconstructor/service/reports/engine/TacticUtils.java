package com.aidigital.reportconstructor.service.reports.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Java port of {@code api/tactic_utils.php} + the data/utility helpers from
 * {@code api/placeholders/resolvers_tactics.php}.
 *
 * <p>Holds the channel mapping, display-name normalisation, KPI-type detection,
 * tactic whitelist and the Media-column extraction used to discover which
 * tactics a media plan contains. Pure data + string logic — no I/O.
 */
@Component
public class TacticUtils {
    private static final String[] STOP_WORDS = {"added value", "totals", "please note", "total:"};

    // ── Media column extraction ───────────────────────────────────────────────

    /** Mirrors PHP {@code extractTacticsFromMedia} — tactics in Media-column order. */
    public List<String> extractTacticsFromMedia(List<List<String>> rows) {

        List<String> out = new ArrayList<>();
        if (rows == null) return out;
        int mediaRow = -1;
        int mediaCol = -1;
        outer:
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row == null) continue;
            for (int j = 0; j < row.size(); j++) {
                if (cell(row, j).toLowerCase(Locale.ROOT).equals("media")) {
                    mediaRow = i;
                    mediaCol = j;
                    break outer;
                }
            }
        }
        if (mediaRow < 0) return out;

        for (int i = mediaRow + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String c = cellAt(row, mediaCol);
            String rowText = joinLower(row, 4);
            boolean stop = false;
            for (String sw : STOP_WORDS) {
                if (rowText.contains(sw)) { stop = true; break; }
            }
            if (stop) break;
            if (c.isEmpty()) break;
            out.add(c);
        }
        return out;
    }

    /** Mirrors PHP {@code countTacticsInMediaPlan} — 0..7 known tactics under "Media". */
    public int countTacticsInMediaPlan(List<List<String>> rows) {

        if (rows == null) return 0;
        int mediaRow = -1;
        int mediaCol = -1;
        outer:
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row == null) continue;
            for (int j = 0; j < row.size(); j++) {
                if (cell(row, j).toLowerCase(Locale.ROOT).equals("media")) {
                    mediaRow = i;
                    mediaCol = j;
                    break outer;
                }
            }
        }
        if (mediaRow < 0) return 0;
        int count = 0;
        int limit = Math.min(mediaRow + 20, rows.size() - 1);
        for (int i = mediaRow + 1; i <= limit; i++) {
            String c = cellAt(rows.get(i), mediaCol);
            if (c.isEmpty()) continue;
            if (KNOWN_TACTICS.containsKey(c.toLowerCase(Locale.ROOT))) count++;
        }
        return count;
    }

    /** Mirrors PHP {@code normalizeTacticDisplayName} — short Slides label or raw name. */
    public String normalizeTacticDisplayName(String rawName) {

        if (rawName == null) return "";
        String key = rawName.trim().toLowerCase(Locale.ROOT);
        return DISPLAY_MAP.getOrDefault(key, rawName);
    }

    /** Whitelist used by {@code resolveTacticsList}: lowercase media name → canonical. */
    public Map<String, String> knownTacticsWhitelist() {
        return WHITELIST;
    }

    /** Mirrors PHP {@code getTacticChannelFilter} — BQ Channel filter or {@code null}. */
    public String getTacticChannelFilter(String tacticName) {

        if (tacticName == null) return null;
        return CHANNEL_MAP.get(tacticName.trim().toLowerCase(Locale.ROOT));
    }

    /** Mirrors PHP {@code getTacticKpiType} — {@code "ctr"}, {@code "vcr"} or {@code null}. */
    public String getTacticKpiType(String tacticName) {

        if (tacticName == null) return null;
        String key = tacticName.trim().toLowerCase(Locale.ROOT);
        String exact = KPI_MAP.get(key);
        if (exact != null) return exact;
        String[] vcrKw = {"video", "ctv", "ott", "netflix", "audio", "sports", "youtube", "streaming", "twitch"};
        String[] ctrKw = {"display", "geofencing", "dooh", "native", "search", "social", "sem", "meta", "tiktok", "linkedin", "pinterest", "reddit", "snapchat", "twitter"};
        for (String kw : vcrKw) if (key.contains(kw)) return "vcr";
        for (String kw : ctrKw) if (key.contains(kw)) return "ctr";
        return null;
    }

    /**
     * Deterministic frequency derived from the planned max frequency.
     *
     * <p>The PHP {@code _tacticFreqCache} used {@code rand(3,15)%}, which differed
     * between the Preview and Generate passes. Here the reduction percentage is a
     * stable function of the tactic index so freq and reach always agree and the
     * deck is reproducible.
     */
    public double freqFromMax(int n, double maxFreq) {

        int pct = 3 + Math.floorMod(n * 7, 13); // 3..15, deterministic
        double freq = maxFreq * (1.0 - pct / 100.0);
        return Math.round(freq * 100.0) / 100.0;
    }

    /** Mirrors PHP {@code sanitizeForSlides}. */
    public String sanitizeForSlides(String value) {

        if (value == null) return "";
        String v = value.replace("\0", "");
        v = v.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
        v = v.replace("\t", " ");
        v = v.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        v = v.replaceAll("  +", " ");
        if (v.length() > 50000) v = v.substring(0, 50000);
        return v.trim();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    String cell(List<String> row, int idx) {

        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    String cellAt(List<String> row, int idx) {

        if (row == null || idx < 0 || idx >= row.size()) return "";
        return cell(row, idx);
    }

    String joinLower(List<String> row, int n) {

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(n, row.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(' ');
            sb.append(cell(row, i));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    // ── DATA: tacticChannelMap() ──────────────────────────────────────────────
    private static final Map<String, String> CHANNEL_MAP = new LinkedHashMap<>();
    static {
        Map<String, String> m = CHANNEL_MAP;
        m.put( "blended set ctv/ott", "CTV/OTT");
        m.put( "blended set ctv ott", "CTV/OTT");
        m.put( "ott precision reach", "OTT");
        m.put( "programmatic ctv", "CTV");
        m.put( "streaming tv", "CTV");
        m.put( "ctv precision reach", "CTV");
        m.put( "ctv select", "CTV");
        m.put( "network select bundle ctv", "CTV");
        m.put( "network exclusive", "CTV");
        m.put( "live news", "CTV");
        m.put( "live tv", "CTV");
        m.put( "local ctv", "CTV");
        m.put( "zip code targeted ctv", "CTV");
        m.put( "100% live sports package (100% live and in-game inventory)", "CTV Live Sports");
        m.put( "any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)", "CTV Live Sports");
        m.put( "college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)", "CTV Live Sports");
        m.put( "amazon fire tv", "CTV");
        m.put( "google tv", "CTV");
        m.put( "netflix (up to 10 sec creative)", "CTV");
        m.put( "netflix (up to 15 sec creative)", "CTV");
        m.put( "netflix (up to 30 sec creative)", "CTV");
        m.put( "youtube skippable in-stream", "YouTube");
        m.put( "youtube skippable in-stream (cpm)", "YouTube");
        m.put( "youtube non-skippable in-stream", "YouTube");
        m.put( "youtube ctv skippable in-stream", "YouTube");
        m.put( "youtube ctv non-skippable in-stream", "YouTube");
        m.put( "youtube in-feed (ex. discovery)", "YouTube");
        m.put( "youtube bumper ads", "YouTube");
        m.put( "youtube demand gen", "YouTube");
        m.put( "youtube shorts", "YouTube");
        m.put( "youtube tv (up to 15 sec)", "YouTube");
        m.put( "youtube tv (up to 30 sec)", "YouTube");
        m.put( "mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)", "YouTube");
        m.put( "mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", "YouTube");
        m.put( "meta (cpm)", "Meta");
        m.put( "meta (cpc)", "Meta");
        m.put( "facebook specific", "Meta");
        m.put( "meta lead forms", "Meta");
        m.put( "meta boosted posts", "Meta");
        m.put( "instagram specific", "Meta");
        m.put( "twitter", "Twitter");
        m.put( "linkedin (cpm)", "LinkedIn");
        m.put( "linkedin (cpc)", "LinkedIn");
        m.put( "tiktok (cpm)", "TikTok");
        m.put( "tiktok (cpc)", "TikTok");
        m.put( "tiktok spark ads (cpm)", "TikTok");
        m.put( "tiktok spark ads (cpc)", "TikTok");
        m.put( "tiktok search ads", "TikTok");
        m.put( "pinterest (cpm)", "Pinterest");
        m.put( "pinterest (cpc)", "Pinterest");
        m.put( "reddit (cpm)", "Reddit");
        m.put( "reddit (cpc)", "Reddit");
        m.put( "snapchat (cpm)", "Snapchat");
        m.put( "programmatic display", "Display");
        m.put( "rich media (html 5)", "Rich Media");
        m.put( "geofencing (display)", "In-App Display");
        m.put( "programmatic mobile display", "In-App Display");
        m.put( "native display", "Native");
        m.put( "native video", "Native Video");
        m.put( "dooh", "DOOH");
        m.put( "programmatic video", "Video");
        m.put( "programmatic audio", "Audio");
        m.put( "blended programmatic audio", "Audio");
        m.put( "amazon podcast ads", "Audio");
        m.put( "amazon audio (amazon & publisher network)", "Audio");
        m.put( "amazon display (amazon & publisher network)", "Amazon Display");
        m.put( "amazon video (amazon & publisher network)", "Amazon Video");
        m.put( "amazon sponsored ads", "Amazon Search");
        m.put( "google sem", "Google Search");
        m.put( "bing", "Bing Search");
        m.put( "performance max", "Performance Max");
        m.put( "demand gen", "Google Search");
        m.put( "gdn specific", "Display");
        m.put( "app (google uac)", "Google App");
        m.put( "apple search ads", "Apple Search");
        m.put( "twitch", "Amazon Video Twitch");
    }

    // ── DATA: normalizeTacticDisplayName() displayMap ─────────────────────────
    private static final Map<String, String> DISPLAY_MAP = new LinkedHashMap<>();
    static {
        Map<String, String> m = DISPLAY_MAP;
        m.put( "blended set ctv/ott", "CTV/OTT");
        m.put( "blended set ctv ott", "CTV/OTT");
        m.put( "ott precision reach", "OTT");
        m.put( "programmatic ctv", "CTV");
        m.put( "ctv precision reach", "CTV");
        m.put( "ctv select", "CTV");
        m.put( "network select bundle ctv", "CTV");
        m.put( "zip code targeted ctv", "CTV");
        m.put( "100% live sports package (100% live and in-game inventory)", "Live Sports");
        m.put( "any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)", "Live Sports");
        m.put( "any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)", "Live Sports");
        m.put( "college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)", "Live Sports");
        m.put( "live sports package", "Live Sports");
        m.put( "amazon fire tv", "Amazon Fire TV");
        m.put( "google tv", "Google TV");
        m.put( "netflix (up to 10 sec creative)", "Netflix");
        m.put( "netflix (up to 15 sec creative)", "Netflix");
        m.put( "netflix (up to 30 sec creative)", "Netflix");
        m.put( "programmatic display", "Display");
        m.put( "rich media (html 5)", "Rich Media");
        m.put( "geofencing (display)", "GeoFencing");
        m.put( "geofencing display", "GeoFencing");
        m.put( "programmatic mobile display", "Programmatic Mobile");
        m.put( "programmatic video", "Video");
        m.put( "programmatic audio", "Audio");
        m.put( "blended programmatic audio", "Audio");
        m.put( "youtube skippable in-stream", "YouTube In-stream");
        m.put( "youtube skippable in-stream (cpm)", "YouTube In-stream");
        m.put( "youtube non-skippable in-stream", "YouTube In-stream");
        m.put( "youtube ctv skippable in-stream", "YouTube In-stream");
        m.put( "youtube ctv non-skippable in-stream", "YouTube In-stream");
        m.put( "youtube in-feed (ex. discovery)", "YouTube");
        m.put( "youtube bumper ads", "YouTube");
        m.put( "youtube demand gen", "YouTube");
        m.put( "youtube tv (up to 15 sec)", "YouTube");
        m.put( "youtube tv (up to 30 sec)", "YouTube");
        m.put( "mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)", "YouTube");
        m.put( "mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", "YouTube");
        m.put( "meta (cpm)", "Meta");
        m.put( "meta (cpc)", "Meta");
        m.put( "facebook specific", "Meta");
        m.put( "meta lead forms", "Meta");
        m.put( "meta boosted posts", "Meta");
        m.put( "instagram specific", "Instagram");
        m.put( "linkedin (cpm)", "LinkedIn");
        m.put( "linkedin (cpc)", "LinkedIn");
        m.put( "tiktok (cpm)", "TikTok");
        m.put( "tiktok (cpc)", "TikTok");
        m.put( "tiktok spark ads (cpm)", "TikTok");
        m.put( "tiktok spark ads (cpc)", "TikTok");
        m.put( "tiktok search ads", "TikTok");
        m.put( "pinterest (cpm)", "Pinterest");
        m.put( "pinterest (cpc)", "Pinterest");
        m.put( "reddit (cpm)", "Reddit");
        m.put( "reddit (cpc)", "Reddit");
        m.put( "snapchat (cpm)", "Snapchat");
        m.put( "amazon display (amazon & publisher network)", "Amazon Display");
        m.put( "amazon video (amazon & publisher network)", "Amazon Video");
        m.put( "amazon audio (amazon & publisher network)", "Amazon Audio");
        m.put( "amazon podcast ads", "Amazon Podcast");
    }

    // ── DATA: _getKnownTacticsWhitelist() ─────────────────────────────────────
    private static final Map<String, String> WHITELIST = new LinkedHashMap<>();
    static {
        Map<String, String> m = WHITELIST;
        m.put( "programmatic display", "Programmatic Display");
        m.put( "geofencing (display)", "GeoFencing (Display)");
        m.put( "geofencing display", "GeoFencing (Display)");
        m.put( "any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)", "ANY Live Sports Package (Up to 50% Live Sports inventory / Up to 50% Ancillary inventory)");
        m.put( "any live sports package", "ANY Live Sports Package (Up to 50% Live Sports inventory / Up to 50% Ancillary inventory)");
        m.put( "live sports package", "ANY Live Sports Package (Up to 50% Live Sports inventory / Up to 50% Ancillary inventory)");
        m.put( "ctv precision reach", "CTV Precision Reach");
        m.put( "blended set ctv/ott", "Blended Set CTV/OTT");
        m.put( "blended set ctv ott", "Blended Set CTV/OTT");
        m.put( "dooh", "DOOH");
        m.put( "blended programmatic audio", "Blended Programmatic Audio");
        m.put( "programmatic audio", "Blended Programmatic Audio");
        m.put( "netflix (up to 30 sec creative)", "Netflix (Up to 30 sec creative)");
        m.put( "netflix (up to 15 sec creative)", "Netflix (Up to 15 sec creative)");
        m.put( "netflix (up to 10 sec creative)", "Netflix (Up to 10 sec creative)");
        m.put( "netflix", "Netflix (Up to 30 sec creative)");
        m.put( "programmatic video", "Programmatic Video");
        m.put( "meta (cpm)", "Meta (CPM)");
        m.put( "meta (cpc)", "Meta (CPC)");
        m.put( "meta lead forms", "Meta Lead Forms");
        m.put( "meta boosted posts", "Meta Boosted Posts");
        m.put( "facebook specific", "Facebook Specific");
        m.put( "instagram specific", "Instagram Specific");
        m.put( "tiktok (cpm)", "TikTok (CPM)");
        m.put( "tiktok (cpc)", "TikTok (CPC)");
        m.put( "tiktok spark ads (cpm)", "TikTok Spark Ads (CPM)");
        m.put( "tiktok spark ads (cpc)", "TikTok Spark Ads (CPC)");
        m.put( "tiktok search ads", "TikTok Search Ads");
        m.put( "linkedin (cpm)", "LinkedIn (CPM)");
        m.put( "linkedin (cpc)", "LinkedIn (CPC)");
        m.put( "twitter", "Twitter");
        m.put( "pinterest (cpm)", "Pinterest (CPM)");
        m.put( "pinterest (cpc)", "Pinterest (CPC)");
        m.put( "reddit (cpm)", "Reddit (CPM)");
        m.put( "reddit (cpc)", "Reddit (CPC)");
        m.put( "snapchat (cpm)", "Snapchat (CPM)");
        m.put( "youtube skippable in-stream", "YouTube Skippable In-Stream");
        m.put( "youtube skippable in-stream (cpm)", "YouTube Skippable In-Stream (CPM)");
        m.put( "youtube non-skippable in-stream", "YouTube Non-Skippable In-Stream");
        m.put( "youtube ctv skippable in-stream", "YouTube CTV Skippable In-Stream");
        m.put( "youtube ctv non-skippable in-stream", "YouTube CTV Non-Skippable In-Stream");
        m.put( "youtube in-feed (ex. discovery)", "YouTube In-Feed");
        m.put( "youtube bumper ads", "YouTube Bumper Ads");
        m.put( "youtube demand gen", "YouTube Demand Gen");
        m.put( "youtube shorts", "YouTube Shorts");
        m.put( "youtube tv (up to 15 sec)", "YouTube TV (up to 15 sec)");
        m.put( "youtube tv (up to 30 sec)", "YouTube TV (up to 30 sec)");
        m.put( "rich media (html 5)", "Rich Media (HTML5)");
        m.put( "programmatic mobile display", "Programmatic Mobile Display");
        m.put( "native display", "Native Display");
        m.put( "native video", "Native Video");
        m.put( "google sem", "Google SEM");
        m.put( "bing", "Bing");
        m.put( "performance max", "Performance Max");
        m.put( "demand gen", "Demand Gen");
        m.put( "gdn specific", "GDN Specific");
        m.put( "amazon display (amazon & publisher network)", "Amazon Display");
        m.put( "amazon video (amazon & publisher network)", "Amazon Video");
        m.put( "amazon sponsored ads", "Amazon Sponsored Ads");
        m.put( "amazon podcast ads", "Amazon Podcast Ads");
        m.put( "twitch", "Twitch");
    }

    // ── DATA: countTacticsInMediaPlan() knownTactics set ──────────────────────
    private static final Map<String, Boolean> KNOWN_TACTICS = new LinkedHashMap<>();
    static {
        String[] keys = {
            "programmatic display", "geofencing (display)", "geofencing display",
            "any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)",
            "any live sports package", "live sports package", "ctv precision reach",
            "blended set ctv/ott", "blended set ctv ott", "dooh", "blended programmatic audio",
            "programmatic audio", "netflix (up to 30 sec creative)", "netflix (up to 15 sec creative)",
            "netflix (up to 10 sec creative)", "netflix", "programmatic video", "meta (cpm)", "meta (cpc)",
            "meta lead forms", "meta boosted posts", "facebook specific", "instagram specific",
            "tiktok (cpm)", "tiktok (cpc)", "tiktok spark ads (cpm)", "tiktok spark ads (cpc)",
            "tiktok search ads", "linkedin (cpm)", "linkedin (cpc)", "twitter", "pinterest (cpm)",
            "pinterest (cpc)", "reddit (cpm)", "reddit (cpc)", "snapchat (cpm)",
            "youtube skippable in-stream", "youtube skippable in-stream (cpm)",
            "youtube non-skippable in-stream", "youtube ctv skippable in-stream",
            "youtube ctv non-skippable in-stream", "youtube in-feed (ex. discovery)",
            "youtube bumper ads", "youtube demand gen", "youtube shorts", "youtube tv (up to 15 sec)",
            "youtube tv (up to 30 sec)", "rich media (html 5)", "programmatic mobile display",
            "native display", "native video", "google sem", "bing", "performance max", "demand gen",
            "gdn specific", "amazon display (amazon & publisher network)",
            "amazon video (amazon & publisher network)", "amazon sponsored ads", "amazon podcast ads",
            "twitch", "programmatic ctv", "ott precision reach", "ctv select", "network select bundle ctv",
            "network exclusive", "live news", "live tv", "local ctv", "zip code targeted ctv",
            "streaming tv", "100% live sports package (100% live and in-game inventory)",
            "100% live sports package", "college football live sport package", "amazon fire tv",
            "google tv", "youtube in-feed", "mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)",
            "mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", "app (google uac)",
            "google uac", "apple search ads"
        };
        for (String k : keys) KNOWN_TACTICS.put(k, Boolean.TRUE);
    }

    // ── DATA: getTacticKpiType() exactMap ─────────────────────────────────────
    private static final Map<String, String> KPI_MAP = new LinkedHashMap<>();
    static {
        Map<String, String> m = KPI_MAP;
        String[] vcr = {
            "blended set ctv/ott", "blended set ctv ott", "ott precision reach", "programmatic ctv",
            "streaming tv", "ctv precision reach", "ctv select", "network select bundle ctv",
            "network exclusive", "live news", "live tv", "local ctv", "zip code targeted ctv",
            "100% live sports package (100% live and in-game inventory)",
            "any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)",
            "college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)",
            "amazon fire tv", "google tv", "netflix (up to 10 sec creative)", "netflix (up to 15 sec creative)",
            "netflix (up to 30 sec creative)", "youtube skippable in-stream", "youtube skippable in-stream (cpm)",
            "youtube non-skippable in-stream", "youtube ctv skippable in-stream", "youtube ctv non-skippable in-stream",
            "youtube in-feed (ex. discovery)", "youtube bumper ads", "youtube tv (up to 15 sec)",
            "youtube tv (up to 30 sec)", "mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)",
            "mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", "programmatic video",
            "programmatic audio", "blended programmatic audio", "amazon podcast ads",
            "amazon audio (amazon & publisher network)", "amazon video (amazon & publisher network)", "twitch",
            "amazon fire tv", "google tv", "amazon podcast", "live sports", "ctv/ott", "ott", "ctv", "netflix",
            "audio", "video", "youtube", "youtube in-stream"
        };
        String[] ctr = {
            "youtube demand gen", "youtube shorts", "meta (cpm)", "meta (cpc)", "facebook specific",
            "meta lead forms", "meta boosted posts", "instagram specific", "twitter", "linkedin (cpm)",
            "linkedin (cpc)", "tiktok (cpm)", "tiktok (cpc)", "tiktok spark ads (cpm)", "tiktok spark ads (cpc)",
            "tiktok search ads", "pinterest (cpm)", "pinterest (cpc)", "reddit (cpm)", "reddit (cpc)",
            "snapchat (cpm)", "programmatic display", "rich media (html 5)", "geofencing (display)",
            "programmatic mobile display", "native display", "native video", "dooh",
            "amazon display (amazon & publisher network)", "amazon sponsored ads", "google sem", "bing",
            "performance max", "demand gen", "gdn specific", "app (google uac)", "apple search ads",
            "rich media", "programmatic mobile", "instagram", "geofencing", "display", "meta"
        };
        for (String k : vcr) m.put(k, "vcr");
        for (String k : ctr) m.put(k, "ctr");
    }
}
