package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.engine.TacticCatalog;
import com.aidigital.reportconstructor.service.reports.helpers.MediaPlanTacticExtractor;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tactic extraction and lookup helpers for the report engine.
 *
 * <p>Holds the channel mapping, display-name normalisation, KPI-type detection,
 * tactic whitelist and the Media-column extraction used to discover which
 * tactics a media plan contains. Pure data + string logic — no I/O.
 */
@Component
public class TacticExtractionHelperImpl implements TacticExtractionHelper {


	/** Substrings that mark a completion-led tactic as audio, so its completion rate is labelled ACR not VCR. */
	private static final String[] AUDIO_KEYWORDS = {"audio", "podcast"};

	private final TacticCatalog catalog;
	private final MediaPlanTacticExtractor tacticExtractor;

	public TacticExtractionHelperImpl(TacticCatalog catalog, MediaPlanTacticExtractor tacticExtractor) {
		this.catalog = catalog;
		this.tacticExtractor = tacticExtractor;
	}

	// ── Media column extraction ───────────────────────────────────────────────

	@Override
	public List<String> extractTacticsFromMedia(List<List<String>> rows) {

		return tacticExtractor.extract(rows).stream().map(PlanTactic::name).toList();
	}

	@Override
	public boolean isKnownTactic(String mediaCell) {

		return mediaCell != null && catalog.isKnownTactic(mediaCell);
	}

	@Override
	public int countTacticsInMediaPlan(List<List<String>> rows) {

		return tacticExtractor.extract(rows).size();
	}

	@Override
	public String normalizeTacticDisplayName(String rawName) {

		if (rawName == null) {
			return "";
		}
		return catalog.displayFor(rawName);
	}

	@Override
	public Map<String, String> knownTacticsWhitelist() {
		return catalog.whitelist();
	}

	@Override
	public String getTacticChannelFilter(String tacticName) {

		if (tacticName == null) {
			return null;
		}
		return catalog.channelFor(tacticName);
	}

	@Override
	public double volumeCoefficient(String tacticName) {

		return catalog.volumeCoefficient(tacticName);
	}

	@Override
	public String getTacticKpiType(String tacticName) {

		if (tacticName == null) {
			return null;
		}
		String key = tacticName.trim().toLowerCase(Locale.ROOT);
		String exact = catalog.exactKpiType(tacticName);
		if (exact != null) {
			return exact;
		}
		String[] vcrKw = {"video", "ctv", "ott", "netflix", "audio", "sports", "youtube", "streaming", "twitch"};
		String[] ctrKw = {"display", "geofencing", "dooh", "native", "search", "social", "sem", "meta", "tiktok",
				"linkedin", "pinterest", "reddit", "snapchat", "twitter"};
		for (String kw : vcrKw) {
			if (key.contains(kw)) {
				return "vcr";
			}
		}
		for (String kw : ctrKw) {
			if (key.contains(kw)) {
				return "ctr";
			}
		}
		return null;
	}

	@Override
	public String getCompletionRateLabel(String tacticName) {

		if (tacticName != null) {
			String key = tacticName.trim().toLowerCase(Locale.ROOT);
			for (String kw : AUDIO_KEYWORDS) {
				if (key.contains(kw)) {
					return "ACR";
				}
			}
		}
		return "VCR";
	}

	@Override
	public String getTacticKpiSeries(String tacticName) {

		String type = getTacticKpiType(tacticName);
		if ("vcr".equals(type) && "ACR".equals(getCompletionRateLabel(tacticName))) {
			return "acr";
		}
		return type;
	}

	/**
	 * Implementation note: the reduction percentage is a stable function of the tactic index
	 * (never random), so frequency and reach always agree and the deck is reproducible across
	 * the Preview and Generate passes.
	 */
	@Override
	public double freqFromMax(int n, double maxFreq) {

		int pct = 3 + Math.floorMod(n * 7, 13); // 3..15, deterministic
		double freq = maxFreq * (1.0 - pct / 100.0);
		return Math.round(freq * 100.0) / 100.0;
	}

	/**
	 * Implementation note: like {@link #freqFromMax}, the discount is a stable function of the tactic
	 * index (never random), so {@code {{tactic n f}}} and the reach derived from it always agree and
	 * the deck is reproducible across the Preview and Generate passes.
	 */
	@Override
	public double freqFromWeekly(int n, double weeklyFreq, double weeks) {

		int pct = 2 + Math.floorMod(n * 11, 19); // 2..20, deterministic
		double freq = weeklyFreq * weeks * (1.0 - pct / 100.0);
		return Math.round(freq * 100.0) / 100.0;
	}

	@Override
	public String sanitizeForSlides(String value) {

		if (value == null) {
			return "";
		}
		String v = value.replace("\0", "");
		v = v.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
		v = v.replace("\t", " ");
		v = v.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
		v = v.replaceAll("  +", " ");
		if (v.length() > 50000) {
			v = v.substring(0, 50000);
		}
		return v.trim();
	}
}
