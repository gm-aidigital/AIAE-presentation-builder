package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Text normalization for Claude batch responses — ports the PHP char-limit
 * closures used in {@code claude_api.php} so limits are unit-testable without HTTP.
 */
@Component
public class ClaudeResponseNormalizer {

	/**
	 * Extracts concatenated text blocks from an Anthropic Messages API response.
	 *
	 * @param resp parsed JSON body
	 * @return combined text or {@code null} when empty
	 */
	public String extractText(JsonNode resp) {
		JsonNode content = resp.path("content");
		if (!content.isArray() || content.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode part : content) {
			if ("text".equals(part.path("type").asText())) {
				sb.append(part.path("text").asText());
			}
		}
		return sb.toString();
	}

	/**
	 * Finds the last sentence-ending period in {@code window}, treating a period immediately followed by a
	 * digit (e.g. the {@code "."} in {@code "94.72"}) as part of a decimal number rather than a sentence
	 * boundary.
	 *
	 * @param window text scanned for a sentence-ending period
	 * @return index of the last qualifying period, or -1 when none is found
	 */
	int lastSentencePeriod(String window) {
		for (int i = window.length() - 1; i >= 0; i--) {
			if (window.charAt(i) == '.' && (i + 1 >= window.length() || !Character.isDigit(window.charAt(i + 1)))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Trims a trailing dangling comma left at the end of a word-boundary cut, since a list cut off
	 * mid-enumeration reads as unfinished even though it didn't break mid-word.
	 *
	 * @param val text that was just cut at a word boundary
	 * @return {@code val} with a single trailing comma removed, otherwise {@code val} unchanged
	 */
	String stripTrailingComma(String val) {
		return val.endsWith(",") ? val.substring(0, val.length() - 1).trim() : val;
	}

	/**
	 * Batch A {@code proposal_overview}: window limit+120, last real sentence-ending {@code .} (decimal
	 * points excluded) past threshold limit*0.5; falls back to the last word boundary, with any trailing
	 * dangling comma stripped, when no qualifying period is found.
	 *
	 * @param val   raw model text
	 * @param limit character budget before windowing
	 * @return trimmed text or {@code null} when blank
	 */
	public String normalizeProposal(String val, int limit) {
		if (val == null || val.trim().isEmpty()) {
			return null;
		}
		val = val.replaceAll("\\s*[\\r\\n]+\\s*", " ").replaceAll("\\s{2,}", " ").trim();
		if (val.length() > limit) {
			String window = val.substring(0, Math.min(limit + 120, val.length()));
			int lp = lastSentencePeriod(window);
			if (lp >= (int) (limit * 0.5)) {
				val = val.substring(0, lp + 1).trim();
			} else {
				String cut = val.substring(0, limit);
				int ls = cut.lastIndexOf(' ');
				val = stripTrailingComma(ls >= 0 ? val.substring(0, ls).trim() : cut.trim());
			}
		}
		return val.isEmpty() ? null : val;
	}

	/**
	 * Batch C normalize: window=limit, prefers the last real sentence-ending {@code .} (decimal points
	 * excluded) past threshold limit*0.75 (so the result reads as a finished thought); falls back to the
	 * last word boundary (never mid-word), with any trailing dangling comma stripped, when no qualifying
	 * period is found.
	 *
	 * @param val   raw model text
	 * @param limit character budget
	 * @return trimmed text or {@code null} when blank
	 */
	public String normalizeC(String val, int limit) {
		if (val == null || val.trim().isEmpty()) {
			return null;
		}
		val = val.replaceAll("\\s*[\\r\\n]+\\s*", " ").replaceAll("\\s{2,}", " ").trim();
		if (val.length() > limit) {
			String cut = val.substring(0, limit);
			int threshold = (int) (limit * 0.75);
			int lastPeriod = lastSentencePeriod(cut);
			if (lastPeriod > threshold) {
				val = val.substring(0, lastPeriod + 1).trim();
			} else {
				int ls = cut.lastIndexOf(' ');
				val = stripTrailingComma(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
			}
		}
		return val.isEmpty() ? null : val;
	}

	/**
	 * Splits {@code " | "} into exactly four elements (null for blanks/missing).
	 *
	 * @param val thoughts-on-performance field from Batch C
	 * @return four slots, never shorter than four entries
	 */
	public List<String> normalizeThoughts(String val) {
		List<String> out = new ArrayList<>(Arrays.asList(null, null, null, null));
		if (val == null || val.trim().isEmpty()) {
			return out;
		}
		val = val.replaceAll("\\s*[\\r\\n]+\\s*", " ").replaceAll("\\s{2,}", " ").trim();
		String[] parts = val.split(" \\| ", -1);
		for (int i = 0; i < 4; i++) {
			if (i < parts.length) {
				String p = parts[i] == null ? null : parts[i].trim();
				out.set(i, (p == null || p.isEmpty()) ? null : p);
			}
		}
		return out;
	}

	/**
	 * Returns the textual value of a JSON node, falling back to its serialized form for non-text nodes.
	 *
	 * @param node JSON field to read (null or JSON-null yields {@code null})
	 * @return the node's text (or {@code toString()} for non-textual nodes), or {@code null} when absent/empty
	 */
	public String textOrNull(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		String s = node.isTextual() ? node.asText() : node.toString();
		return s == null || s.isEmpty() ? null : s;
	}

	/**
	 * Tests whether a string carries non-whitespace content.
	 *
	 * @param s the string to inspect (may be null)
	 * @return {@code true} when {@code s} is non-null and contains at least one non-whitespace character
	 */
	public boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

	/**
	 * Caps the Batch A {@code audience_segments} copy at 80 characters, trimming back to the last comma when
	 * truncating.
	 *
	 * @param seg raw audience-segments text from the model ({@code "not specified"} is treated as empty)
	 * @return the trimmed segment text, or {@code null} when blank or unspecified
	 */
	public String limitAudienceSegments(String seg) {
		if (seg == null) {
			return null;
		}
		seg = seg.trim();
		if ("not specified".equalsIgnoreCase(seg)) {
			return null;
		}
		if (seg.length() > 80) {
			String cut = seg.substring(0, 80);
			int lc = cut.lastIndexOf(',');
			seg = lc >= 0 ? cut.substring(0, lc).trim() : cut.trim();
		}
		return seg.isEmpty() ? null : seg;
	}

	/**
	 * Caps the Batch A strategic-point placeholder at 22 characters, preferring the last real
	 * sentence-ending {@code .} (decimal points excluded) past position 11 over a hard cut, then falling
	 * back to the last word boundary (never mid-word), with any trailing dangling comma stripped, when no
	 * qualifying period is found.
	 *
	 * @param point raw strategic-point text from the model (may be null)
	 * @return the trimmed point, or an empty string when {@code point} is null
	 */
	public String limitStrategicPoint(String point) {
		if (point == null) {
			return "";
		}
		point = point.trim();
		if (point.length() > 22) {
			String cut = point.substring(0, 22);
			int threshold = 11;
			int lastPeriod = lastSentencePeriod(cut);
			if (lastPeriod > threshold) {
				point = point.substring(0, lastPeriod + 1).trim();
			} else {
				int ls = cut.lastIndexOf(' ');
				point = stripTrailingComma(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
			}
		}
		return point;
	}

	/**
	 * Caps the Batch A strategic overview at 240 characters, preferring the last real sentence-ending
	 * {@code .} (decimal points excluded) past position 180 (so the result reads as a finished thought)
	 * over a hard cut, then falling back to the last word boundary (never mid-word), with any trailing
	 * dangling comma stripped, when no qualifying period is found.
	 *
	 * @param overview raw strategic-overview text from the model (may be null)
	 * @return the trimmed overview, or an empty string when {@code overview} is null
	 */
	public String limitStrategicOverview(String overview) {
		if (overview == null) {
			return "";
		}
		overview = overview.trim();
		if (overview.length() > 240) {
			String cut = overview.substring(0, 240);
			int lastPeriod = lastSentencePeriod(cut);
			if (lastPeriod > 180) {
				overview = overview.substring(0, lastPeriod + 1).trim();
			} else {
				int ls = cut.lastIndexOf(' ');
				overview = stripTrailingComma(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
			}
		}
		return overview;
	}

	/**
	 * Normalizes the Batch C {@code results_overview} copy with a 380-character budget via {@link #normalizeC}.
	 *
	 * @param val raw results-overview text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitResultsOverview(String val) {
		return normalizeC(val, 380);
	}

	/**
	 * Normalizes the Batch C {@code tactic_overview} copy with a 210-character budget via {@link #normalizeC}.
	 *
	 * @param val raw tactic-overview text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitTacticOverview(String val) {
		return normalizeC(val, 210);
	}

	/**
	 * Collapses whitespace in the geo-tab summary and caps it at 40 characters, preferring the last real
	 * sentence-ending {@code .} (decimal points excluded) past position 20 over a hard cut, then falling
	 * back to the last word boundary (never mid-word), with any trailing dangling comma stripped, when no
	 * qualifying period is found.
	 *
	 * @param text raw geo-summary text from the model (may be null or blank)
	 * @return the whitespace-collapsed, length-capped summary, or {@code null} when blank
	 */
	public String limitGeoSummary(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		text = text.replaceAll("\\s*[\\r\\n]+\\s*", " ").replaceAll("\\s{2,}", " ").trim();
		if (text.length() > 40) {
			String cut = text.substring(0, 40);
			int threshold = 20;
			int lastPeriod = lastSentencePeriod(cut);
			if (lastPeriod > threshold) {
				text = text.substring(0, lastPeriod + 1).trim();
			} else {
				int ls = cut.lastIndexOf(' ');
				text = stripTrailingComma(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
			}
		}
		return text.isEmpty() ? null : text;
	}
}
