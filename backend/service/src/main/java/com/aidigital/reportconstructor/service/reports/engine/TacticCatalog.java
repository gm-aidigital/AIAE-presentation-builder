package com.aidigital.reportconstructor.service.reports.engine;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable tactic channel, display, whitelist, and KPI lookup tables. Separated from
 * the tactic extraction helpers so media-plan algorithms stay testable without loading
 * large static blocks.
 */
@Component
public class TacticCatalog {

	// ── DATA: tacticChannelMap() ──────────────────────────────────────────────
	private final Map<String, String> channelMap = new LinkedHashMap<>();

	{
		Map<String, String> m = channelMap;
		m.put("blended set ctv/ott", "CTV/OTT");
		m.put("blended set ctv ott", "CTV/OTT");
		m.put("ott precision reach", "OTT");
		m.put("programmatic ctv", "CTV");
		m.put("streaming tv", "CTV");
		m.put("ctv precision reach", "CTV");
		m.put("ctv select", "CTV");
		m.put("network select bundle ctv", "CTV");
		m.put("network exclusive", "CTV");
		m.put("live news", "CTV");
		m.put("live tv", "CTV");
		m.put("local ctv", "CTV");
		m.put("zip code targeted ctv", "CTV");
		m.put("100% live sports package (100% live and in-game inventory)", "CTV Live Sports");
		m.put("any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				"CTV Live Sports");
		m.put("college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				"CTV Live Sports");
		m.put("amazon fire tv", "CTV");
		m.put("google tv", "CTV");
		m.put("netflix (up to 10 sec creative)", "CTV");
		m.put("netflix (up to 15 sec creative)", "CTV");
		m.put("netflix (up to 30 sec creative)", "CTV");
		m.put("youtube skippable in-stream", "YouTube");
		m.put("youtube skippable in-stream (cpm)", "YouTube");
		m.put("youtube non-skippable in-stream", "YouTube");
		m.put("youtube ctv skippable in-stream", "YouTube");
		m.put("youtube ctv non-skippable in-stream", "YouTube");
		m.put("youtube in-feed (ex. discovery)", "YouTube");
		m.put("youtube bumper ads", "YouTube");
		m.put("youtube demand gen", "YouTube");
		m.put("youtube shorts", "YouTube");
		m.put("youtube tv (up to 15 sec)", "YouTube");
		m.put("youtube tv (up to 30 sec)", "YouTube");
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)", "YouTube");
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", "YouTube");
		m.put("meta (cpm)", "Meta");
		m.put("meta (cpc)", "Meta");
		m.put("facebook specific", "Meta");
		m.put("meta lead forms", "Meta");
		m.put("meta boosted posts", "Meta");
		m.put("instagram specific", "Meta");
		m.put("twitter", "Twitter");
		m.put("linkedin (cpm)", "LinkedIn");
		m.put("linkedin (cpc)", "LinkedIn");
		m.put("tiktok (cpm)", "TikTok");
		m.put("tiktok (cpc)", "TikTok");
		m.put("tiktok spark ads (cpm)", "TikTok");
		m.put("tiktok spark ads (cpc)", "TikTok");
		m.put("tiktok search ads", "TikTok");
		m.put("pinterest (cpm)", "Pinterest");
		m.put("pinterest (cpc)", "Pinterest");
		m.put("reddit (cpm)", "Reddit");
		m.put("reddit (cpc)", "Reddit");
		m.put("snapchat (cpm)", "Snapchat");
		m.put("programmatic display", "Display");
		m.put("rich media (html 5)", "Rich Media");
		m.put("geofencing (display)", "In-App Display");
		m.put("programmatic mobile display", "In-App Display");
		m.put("native display", "Native");
		m.put("native video", "Native Video");
		m.put("dooh", "DOOH");
		m.put("programmatic video", "Video");
		m.put("programmatic audio", "Audio");
		m.put("blended programmatic audio", "Audio");
		m.put("amazon podcast ads", "Audio");
		m.put("amazon audio (amazon & publisher network)", "Audio");
		m.put("amazon display (amazon & publisher network)", "Amazon Display");
		m.put("amazon video (amazon & publisher network)", "Amazon Video");
		m.put("amazon sponsored ads", "Amazon Search");
		m.put("google sem", "Google Search");
		m.put("bing", "Bing Search");
		m.put("performance max", "Performance Max");
		m.put("demand gen", "Google Search");
		m.put("gdn specific", "Display");
		m.put("app (google uac)", "Google App");
		m.put("apple search ads", "Apple Search");
		m.put("twitch", "Amazon Video Twitch");
	}

	// ── DATA: normalizeTacticDisplayName() displayMap ─────────────────────────
	private final Map<String, String> displayMap = new LinkedHashMap<>();

	{
		Map<String, String> m = displayMap;
		m.put("blended set ctv/ott", "CTV/OTT");
		m.put("blended set ctv ott", "CTV/OTT");
		m.put("ott precision reach", "OTT");
		m.put("programmatic ctv", "CTV");
		m.put("ctv precision reach", "CTV");
		m.put("ctv select", "CTV");
		m.put("network select bundle ctv", "CTV");
		m.put("zip code targeted ctv", "CTV");
		m.put("100% live sports package (100% live and in-game inventory)", "Live Sports");
		m.put("any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				"Live Sports");
		m.put("any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)",
				"Live Sports");
		m.put("college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				"Live Sports");
		m.put("live sports package", "Live Sports");
		m.put("amazon fire tv", "Amazon Fire TV");
		m.put("google tv", "Google TV");
		m.put("netflix (up to 10 sec creative)", "Netflix");
		m.put("netflix (up to 15 sec creative)", "Netflix");
		m.put("netflix (up to 30 sec creative)", "Netflix");
		m.put("programmatic display", "Display");
		m.put("rich media (html 5)", "Rich Media");
		m.put("geofencing (display)", "GeoFencing");
		m.put("geofencing display", "GeoFencing");
		m.put("programmatic mobile display", "Programmatic Mobile");
		m.put("programmatic video", "Video");
		m.put("programmatic audio", "Audio");
		m.put("blended programmatic audio", "Audio");
		m.put("youtube skippable in-stream", "YouTube In-stream");
		m.put("youtube skippable in-stream (cpm)", "YouTube In-stream");
		m.put("youtube non-skippable in-stream", "YouTube In-stream");
		m.put("youtube ctv skippable in-stream", "YouTube In-stream");
		m.put("youtube ctv non-skippable in-stream", "YouTube In-stream");
		m.put("youtube in-feed (ex. discovery)", "YouTube");
		m.put("youtube bumper ads", "YouTube");
		m.put("youtube demand gen", "YouTube");
		m.put("youtube tv (up to 15 sec)", "YouTube");
		m.put("youtube tv (up to 30 sec)", "YouTube");
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 15 sec)", "YouTube");
		m.put("mix of 50% youtube tv and 50% youtube ctv (up to 30 sec)", "YouTube");
		m.put("meta (cpm)", "Meta");
		m.put("meta (cpc)", "Meta");
		m.put("facebook specific", "Meta");
		m.put("meta lead forms", "Meta");
		m.put("meta boosted posts", "Meta");
		m.put("instagram specific", "Instagram");
		m.put("linkedin (cpm)", "LinkedIn");
		m.put("linkedin (cpc)", "LinkedIn");
		m.put("tiktok (cpm)", "TikTok");
		m.put("tiktok (cpc)", "TikTok");
		m.put("tiktok spark ads (cpm)", "TikTok");
		m.put("tiktok spark ads (cpc)", "TikTok");
		m.put("tiktok search ads", "TikTok");
		m.put("pinterest (cpm)", "Pinterest");
		m.put("pinterest (cpc)", "Pinterest");
		m.put("reddit (cpm)", "Reddit");
		m.put("reddit (cpc)", "Reddit");
		m.put("snapchat (cpm)", "Snapchat");
		m.put("amazon display (amazon & publisher network)", "Amazon Display");
		m.put("amazon video (amazon & publisher network)", "Amazon Video");
		m.put("amazon audio (amazon & publisher network)", "Amazon Audio");
		m.put("amazon podcast ads", "Amazon Podcast");
	}

	// ── DATA: _getKnownTacticsWhitelist() ─────────────────────────────────────
	private final Map<String, String> whitelistMap = new LinkedHashMap<>();

	{
		Map<String, String> m = whitelistMap;
		m.put("programmatic display", "Programmatic Display");
		m.put("geofencing (display)", "GeoFencing (Display)");
		m.put("geofencing display", "GeoFencing (Display)");
		m.put("any live sports package (up to 50% live sports inventory / up to 50% ancillary inventory)",
				"ANY Live Sports Package (Up to 50% Live Sports inventory / Up to 50% Ancillary inventory)");
		m.put("any live sports package",
				"ANY Live Sports Package (Up to 50% Live Sports inventory / Up to 50% Ancillary inventory)");
		m.put("live sports package",
				"ANY Live Sports Package (Up to 50% Live Sports inventory / Up to 50% Ancillary inventory)");
		m.put("ctv precision reach", "CTV Precision Reach");
		m.put("blended set ctv/ott", "Blended Set CTV/OTT");
		m.put("blended set ctv ott", "Blended Set CTV/OTT");
		m.put("dooh", "DOOH");
		m.put("blended programmatic audio", "Blended Programmatic Audio");
		m.put("programmatic audio", "Blended Programmatic Audio");
		m.put("netflix (up to 30 sec creative)", "Netflix (Up to 30 sec creative)");
		m.put("netflix (up to 15 sec creative)", "Netflix (Up to 15 sec creative)");
		m.put("netflix (up to 10 sec creative)", "Netflix (Up to 10 sec creative)");
		m.put("netflix", "Netflix (Up to 30 sec creative)");
		m.put("programmatic video", "Programmatic Video");
		m.put("meta (cpm)", "Meta (CPM)");
		m.put("meta (cpc)", "Meta (CPC)");
		m.put("meta lead forms", "Meta Lead Forms");
		m.put("meta boosted posts", "Meta Boosted Posts");
		m.put("facebook specific", "Facebook Specific");
		m.put("instagram specific", "Instagram Specific");
		m.put("tiktok (cpm)", "TikTok (CPM)");
		m.put("tiktok (cpc)", "TikTok (CPC)");
		m.put("tiktok spark ads (cpm)", "TikTok Spark Ads (CPM)");
		m.put("tiktok spark ads (cpc)", "TikTok Spark Ads (CPC)");
		m.put("tiktok search ads", "TikTok Search Ads");
		m.put("linkedin (cpm)", "LinkedIn (CPM)");
		m.put("linkedin (cpc)", "LinkedIn (CPC)");
		m.put("twitter", "Twitter");
		m.put("pinterest (cpm)", "Pinterest (CPM)");
		m.put("pinterest (cpc)", "Pinterest (CPC)");
		m.put("reddit (cpm)", "Reddit (CPM)");
		m.put("reddit (cpc)", "Reddit (CPC)");
		m.put("snapchat (cpm)", "Snapchat (CPM)");
		m.put("youtube skippable in-stream", "YouTube Skippable In-Stream");
		m.put("youtube skippable in-stream (cpm)", "YouTube Skippable In-Stream (CPM)");
		m.put("youtube non-skippable in-stream", "YouTube Non-Skippable In-Stream");
		m.put("youtube ctv skippable in-stream", "YouTube CTV Skippable In-Stream");
		m.put("youtube ctv non-skippable in-stream", "YouTube CTV Non-Skippable In-Stream");
		m.put("youtube in-feed (ex. discovery)", "YouTube In-Feed");
		m.put("youtube bumper ads", "YouTube Bumper Ads");
		m.put("youtube demand gen", "YouTube Demand Gen");
		m.put("youtube shorts", "YouTube Shorts");
		m.put("youtube tv (up to 15 sec)", "YouTube TV (up to 15 sec)");
		m.put("youtube tv (up to 30 sec)", "YouTube TV (up to 30 sec)");
		m.put("rich media (html 5)", "Rich Media (HTML5)");
		m.put("programmatic mobile display", "Programmatic Mobile Display");
		m.put("native display", "Native Display");
		m.put("native video", "Native Video");
		m.put("google sem", "Google SEM");
		m.put("bing", "Bing");
		m.put("performance max", "Performance Max");
		m.put("demand gen", "Demand Gen");
		m.put("gdn specific", "GDN Specific");
		m.put("amazon display (amazon & publisher network)", "Amazon Display");
		m.put("amazon video (amazon & publisher network)", "Amazon Video");
		m.put("amazon sponsored ads", "Amazon Sponsored Ads");
		m.put("amazon podcast ads", "Amazon Podcast Ads");
		m.put("twitch", "Twitch");
	}

	// ── DATA: countTacticsInMediaPlan() knownTactics set ──────────────────────
	private final Map<String, Boolean> knownTactics = new LinkedHashMap<>();

	{
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
		for (String k : keys) {
			knownTactics.put(k, Boolean.TRUE);
		}
	}

	// ── DATA: getTacticKpiType() exactMap ─────────────────────────────────────
	private final Map<String, String> kpiMap = new LinkedHashMap<>();

	{
		Map<String, String> m = kpiMap;
		String[] vcr = {
				"blended set ctv/ott", "blended set ctv ott", "ott precision reach", "programmatic ctv",
				"streaming tv", "ctv precision reach", "ctv select", "network select bundle ctv",
				"network exclusive", "live news", "live tv", "local ctv", "zip code targeted ctv",
				"100% live sports package (100% live and in-game inventory)",
				"any live sports package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				"college football live sport package (up to 50% live sports inventory / up to 50% shoulder inventory)",
				"amazon fire tv", "google tv", "netflix (up to 10 sec creative)", "netflix (up to 15 sec creative)",
				"netflix (up to 30 sec creative)", "youtube skippable in-stream", "youtube skippable in-stream (cpm)",
				"youtube non-skippable in-stream", "youtube ctv skippable in-stream", "youtube ctv non-skippable " +
				"in-stream",
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
		for (String k : vcr) {
			m.put(k, "vcr");
		}
		for (String k : ctr) {
			m.put(k, "ctr");
		}
	}

	/**
	 * BQ channel filter for a media-plan tactic name.
	 *
	 * @param tacticName media-plan tactic name (may be {@code null})
	 * @return the mapped BigQuery channel, or {@code null} when unmapped or {@code tacticName} is null
	 */
	public String channelFor(String tacticName) {
		if (tacticName == null) {
			return null;
		}
		return channelMap.get(tacticName.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Short Slides label for a raw media-plan name.
	 *
	 * @param rawName raw media-plan tactic name (may be {@code null})
	 * @return the mapped short label, the unchanged name when unmapped, or {@code ""} when null
	 */
	public String displayFor(String rawName) {
		if (rawName == null) {
			return "";
		}
		String key = rawName.trim().toLowerCase(Locale.ROOT);
		return displayMap.getOrDefault(key, rawName);
	}

	/**
	 * Returns the tactic whitelist used when resolving tactic lists.
	 *
	 * @return map of lowercase media name to its canonical whitelist entry
	 */
	public Map<String, String> whitelist() {
		return whitelistMap;
	}

	/**
	 * Whether a media cell value is a known tactic (used for media-plan counting).
	 *
	 * @param mediaCell the media-column cell value to check
	 * @return {@code true} when the lowercased value is a recognised tactic
	 */
	public boolean isKnownTactic(String mediaCell) {
		return knownTactics.containsKey(mediaCell.toLowerCase(Locale.ROOT));
	}

	/**
	 * Exact KPI type from the lookup table only ({@code ctr}/{@code vcr}).
	 *
	 * @param tacticName media-plan tactic name (may be {@code null})
	 * @return {@code "ctr"}, {@code "vcr"}, or {@code null} when unmapped or {@code tacticName} is null
	 */
	public String exactKpiType(String tacticName) {
		if (tacticName == null) {
			return null;
		}
		return kpiMap.get(tacticName.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Lowercase row-prefix stop phrases used when scanning sheet and media rows.
	 *
	 * @return the immutable list of stop-word prefixes
	 */
	public List<String> sheetStopWords() {
		return List.of("added value", "totals", "please note", "total:");
	}
}
