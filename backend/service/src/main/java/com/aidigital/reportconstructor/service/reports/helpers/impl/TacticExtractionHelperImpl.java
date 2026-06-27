package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.engine.TacticCatalog;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
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


	private final TacticCatalog catalog;
	private final SheetRowHelper sheetRows;

	public TacticExtractionHelperImpl(TacticCatalog catalog, SheetRowHelper sheetRows) {
		this.catalog = catalog;
		this.sheetRows = sheetRows;
	}

	// ── Media column extraction ───────────────────────────────────────────────

	@Override
	public List<String> extractTacticsFromMedia(List<List<String>> rows) {

		List<String> out = new ArrayList<>();
		if (rows == null) {
			return out;
		}
		int mediaRow = -1;
		int mediaCol = -1;
		outer:
		for (int i = 0; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (sheetRows.cellAt(row, j).toLowerCase(Locale.ROOT).equals("media")) {
					mediaRow = i;
					mediaCol = j;
					break outer;
				}
			}
		}
		if (mediaRow < 0) {
			return out;
		}

		for (int i = mediaRow + 1; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			String c = sheetRows.cellAt(row, mediaCol);
			String rowText = sheetRows.joinLower(row, 4);
			boolean stop = false;
			for (String sw : catalog.sheetStopWords()) {
				if (rowText.contains(sw)) {
					stop = true;
					break;
				}
			}
			if (stop) {
				break;
			}
			if (c.isEmpty()) {
				break;
			}
			out.add(c);
		}
		return out;
	}

	@Override
	public int countTacticsInMediaPlan(List<List<String>> rows) {

		if (rows == null) {
			return 0;
		}
		int mediaRow = -1;
		int mediaCol = -1;
		outer:
		for (int i = 0; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.size(); j++) {
				if (sheetRows.cellAt(row, j).toLowerCase(Locale.ROOT).equals("media")) {
					mediaRow = i;
					mediaCol = j;
					break outer;
				}
			}
		}
		if (mediaRow < 0) {
			return 0;
		}
		int count = 0;
		int limit = Math.min(mediaRow + 20, rows.size() - 1);
		for (int i = mediaRow + 1; i <= limit; i++) {
			String c = sheetRows.cellAt(rows.get(i), mediaCol);
			if (c.isEmpty()) {
				continue;
			}
			if (catalog.isKnownTactic(c)) {
				count++;
			}
		}
		return count;
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
