package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Text normalization for Claude batch responses — ports the PHP char-limit
 * closures used in {@code claude_api.php} so limits are unit-testable without HTTP.
 */
@Component
public class ClaudeResponseNormalizer {

	/**
	 * Number of "thoughts on performance" paragraphs the campaign slide holds, and therefore the fixed length
	 * of the slot list {@code normalizeThoughts} returns regardless of how many paragraphs the reply carried.
	 */
	private static final int THOUGHT_SLOTS = 4;

	/**
	 * Characters the end-of-campaign template's audience-segments line fits. The end-of-month north-star
	 * slide gives the same copy a wider box and passes its own budget to
	 * {@link #limitAudienceSegments(String, int)}.
	 */
	public static final int AUDIENCE_SEGMENTS_LIMIT = 80;

	/** Characters the EOM north-star headline fits, upper-cased, on one line. */
	private static final int NORTH_STAR_LIMIT = 80;

	/** Characters the EOM north-star supporting paragraph fits. */
	private static final int EXTENDED_NORTH_STAR_LIMIT = 340;

	/** Characters the EOM horizon block fits. */
	private static final int HORIZON_LIMIT = 150;

	/** Characters one EOM pacing-dashboard key takeaway fits under its table. */
	public static final int PACING_TAKEAWAY_LIMIT = 140;

	/**
	 * Characters one heading of the EOM "what we did this month" slide fits — the label above an
	 * observation, an action or an expected impact.
	 */
	public static final int WHAT_WE_DID_HEADING_LIMIT = 60;

	/** Characters one paragraph of the EOM "what we did this month" slide fits under its heading. */
	public static final int WHAT_WE_DID_TEXT_LIMIT = 200;

	/**
	 * Function/connector words that read as unfinished when a sentence is cut right after them, so they are
	 * trimmed from the tail of a hard word-boundary cut before a closing period is appended.
	 */
	private static final Set<String> DANGLING_TAIL_WORDS = Set.of(
			"a", "an", "the", "and", "or", "but", "nor", "so", "yet", "to", "of", "in", "on", "at", "by",
			"for", "with", "from", "as", "is", "are", "was", "were", "be", "been", "being", "that", "which",
			"who", "this", "these", "those", "into", "than", "then", "while", "where", "when", "because",
			"such", "via", "per", "vs", "its", "their", "our", "his", "her", "your", "about", "over", "under",
			"across", "within", "between", "through", "during", "against", "toward", "towards", "upon");

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
	 * Turns a prose fragment left by a hard word-boundary cut into something that reads as a finished
	 * sentence: strips trailing punctuation and connectors, drops trailing dangling function words (e.g.
	 * {@code "is"}, {@code "for"}, {@code "and"}), and appends a period when the result does not already end
	 * with sentence-ending punctuation. Returns {@code val} unchanged when it already ends in {@code . ! ?}
	 * or when trimming would leave nothing.
	 *
	 * @param val a prose fragment that was cut at a word boundary (never mid-word)
	 * @return the fragment rewritten to end on a complete thought, or {@code val} unchanged when already complete
	 */
	String finishSentence(String val) {
		if (val == null || val.isEmpty()) {
			return val;
		}
		String trimmed = val.strip();
		if (endsWithSentencePunctuation(trimmed)) {
			return trimmed;
		}
		String working = trimmed.replaceAll("[\\s,;:\\-–—]+$", "");
		boolean changed = true;
		while (changed && !working.isEmpty()) {
			changed = false;
			int lastSpace = working.lastIndexOf(' ');
			String lastWord = working.substring(lastSpace + 1);
			String bare = lastWord.replaceAll("[^\\p{L}]", "").toLowerCase();
			if (!bare.isEmpty() && DANGLING_TAIL_WORDS.contains(bare)) {
				working = lastSpace < 0 ? "" : working.substring(0, lastSpace).replaceAll("[\\s,;:\\-–—]+$", "");
				changed = true;
			}
		}
		if (working.isEmpty()) {
			return trimmed;
		}
		if (endsWithSentencePunctuation(working)) {
			return working;
		}
		// A single token with no spaces is not a sentence (e.g. an unbroken blob); leave it as-is rather
		// than append a period that would also overflow the budget by one character.
		return working.indexOf(' ') < 0 ? working : working + ".";
	}

	/**
	 * Reports whether the text already closes on sentence-ending punctuation.
	 *
	 * @param val text to inspect (assumed non-null and stripped)
	 * @return {@code true} when {@code val} ends with {@code .}, {@code !} or {@code ?}
	 */
	boolean endsWithSentencePunctuation(String val) {
		return val.endsWith(".") || val.endsWith("!") || val.endsWith("?");
	}

	/**
	 * Batch A {@code proposal_overview}: window limit+120, last real sentence-ending {@code .} (decimal
	 * points excluded) past threshold limit*0.5; falls back to the last word boundary and rewrites the tail
	 * to end on a complete sentence (see {@link #finishSentence}) when no qualifying period is found.
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
				val = finishSentence(ls >= 0 ? val.substring(0, ls).trim() : cut.trim());
			}
		}
		return val.isEmpty() ? null : val;
	}

	/**
	 * Batch C normalize: window=limit, prefers the last real sentence-ending {@code .} (decimal points
	 * excluded) past threshold limit*0.75 (so the result reads as a finished thought); falls back to the
	 * last word boundary (never mid-word) and rewrites the tail to end on a complete sentence (see
	 * {@link #finishSentence}) when no qualifying period is found.
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
				val = finishSentence(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
			}
		}
		return val.isEmpty() ? null : val;
	}

	/**
	 * Reads the thoughts-on-performance field straight off the parsed reply, accepting either shape the model
	 * produces: a JSON array of paragraphs (the shape the compression prompt asks for, which the campaign call
	 * drifts into) or the pipe-joined string the campaign prompt asks for. Taking the node rather than its text
	 * matters — {@link #textOrNull} renders an array as its raw JSON, which the string path cannot split, so an
	 * array reply used to collapse into slot one and ship {@code ["a","b",…]} onto the slide.
	 *
	 * @param node the {@code thoughts_on_performance} node, possibly absent, textual or an array
	 * @return four slots, never shorter than four entries (null for blank or missing paragraphs)
	 */
	public List<String> normalizeThoughts(JsonNode node) {
		if (node != null && node.isArray()) {
			List<String> out = new ArrayList<>(Arrays.asList(null, null, null, null));
			for (int i = 0; i < THOUGHT_SLOTS && i < node.size(); i++) {
				String p = node.get(i).asText("").trim();
				out.set(i, p.isEmpty() ? null : p);
			}
			return out;
		}
		return normalizeThoughts(textOrNull(node));
	}

	/**
	 * Splits a pipe-joined thoughts string into exactly four elements (null for blanks/missing). The separator
	 * is matched with any surrounding whitespace, so a reply that writes {@code a|b} or {@code a |b} instead of
	 * the requested {@code a | b} still lands one paragraph per slot rather than collapsing into the first.
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
		String[] parts = val.split("\\s*\\|\\s*", -1);
		for (int i = 0; i < THOUGHT_SLOTS; i++) {
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
	 * Caps the Batch A {@code audience_segments} copy at the end-of-campaign budget of
	 * {@link #AUDIENCE_SEGMENTS_LIMIT} characters.
	 *
	 * @param seg raw audience-segments text from the model ({@code "not specified"} is treated as empty)
	 * @return the trimmed segment text, or {@code null} when blank or unspecified
	 */
	public String limitAudienceSegments(String seg) {
		return limitAudienceSegments(seg, AUDIENCE_SEGMENTS_LIMIT);
	}

	/**
	 * Caps the Batch A {@code audience_segments} copy at the given budget, trimming back to the last comma
	 * when truncating. The budget is the flavour's, not a constant, because the EOM north-star slide gives
	 * the segments line a wider box than the EOC template does.
	 *
	 * @param seg   raw audience-segments text from the model ({@code "not specified"} is treated as empty)
	 * @param limit the maximum number of characters the flavour's slide fits
	 * @return the trimmed segment text, or {@code null} when blank or unspecified
	 */
	public String limitAudienceSegments(String seg, int limit) {
		if (seg == null) {
			return null;
		}
		seg = seg.trim();
		if ("not specified".equalsIgnoreCase(seg)) {
			return null;
		}
		if (seg.length() > limit) {
			String cut = seg.substring(0, limit);
			int lc = cut.lastIndexOf(',');
			seg = lc >= 0 ? cut.substring(0, lc).trim() : cut.trim();
		}
		return seg.isEmpty() ? null : seg;
	}

	/**
	 * Caps the EOM {@code north_star} headline at {@link #NORTH_STAR_LIMIT} characters and upper-cases it.
	 *
	 * <p>It is a headline, not a sentence: it is never forced to end on a period, is cut back to the last
	 * word boundary rather than mid-word, loses any dangling comma, and is upper-cased because the slide
	 * prints it in caps.
	 *
	 * @param northStar raw north-star text from the model (may be null)
	 * @return the trimmed, upper-cased headline, or {@code null} when blank
	 */
	public String limitNorthStar(String northStar) {
		if (northStar == null) {
			return null;
		}
		String text = northStar.trim().replaceAll("\\s+", " ");
		if (text.length() > NORTH_STAR_LIMIT) {
			String cut = text.substring(0, NORTH_STAR_LIMIT);
			int ls = cut.lastIndexOf(' ');
			text = stripTrailingComma(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
		}
		text = stripTrailingPeriod(text);
		return text.isEmpty() ? null : text.toUpperCase(Locale.ROOT);
	}

	/**
	 * Normalizes the EOM {@code extended_north_star} copy with a {@link #EXTENDED_NORTH_STAR_LIMIT}-character
	 * budget via {@link #normalizeC}, so it reads as a finished thought.
	 *
	 * @param val raw extended-north-star text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitExtendedNorthStar(String val) {
		return normalizeC(val, EXTENDED_NORTH_STAR_LIMIT);
	}

	/**
	 * Normalizes the EOM {@code horizon} copy with a {@link #HORIZON_LIMIT}-character budget via
	 * {@link #normalizeC}.
	 *
	 * @param val raw horizon text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitHorizon(String val) {
		return normalizeC(val, HORIZON_LIMIT);
	}

	/**
	 * Normalizes one EOM pacing-dashboard key takeaway with a {@link #PACING_TAKEAWAY_LIMIT}-character
	 * budget via {@link #normalizeC}, so it reads as a finished sentence inside the slide's takeaway strip.
	 *
	 * @param val raw takeaway text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitPacingTakeaway(String val) {
		return normalizeC(val, PACING_TAKEAWAY_LIMIT);
	}

	/**
	 * Caps one heading of the EOM "what we did this month" slide at {@link #WHAT_WE_DID_HEADING_LIMIT}
	 * characters.
	 *
	 * <p>Held to the same shape as the north-star headline rather than to {@link #normalizeC}: it is a label
	 * above a paragraph, not a sentence, so it is cut back to a word boundary and never gains a closing
	 * period. The upper-casing is not wanted here — the slide prints these headings in sentence case.
	 *
	 * @param val raw heading text from the model (may be null)
	 * @return the trimmed heading, or {@code null} when blank
	 */
	public String limitWhatWeDidHeading(String val) {
		if (val == null) {
			return null;
		}
		String text = val.trim().replaceAll("\\s+", " ");
		if (text.length() > WHAT_WE_DID_HEADING_LIMIT) {
			String cut = text.substring(0, WHAT_WE_DID_HEADING_LIMIT);
			int ls = cut.lastIndexOf(' ');
			text = stripTrailingComma(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
		}
		text = stripTrailingPeriod(text);
		return text.isEmpty() ? null : text;
	}

	/**
	 * Normalizes one paragraph of the EOM "what we did this month" slide with a
	 * {@link #WHAT_WE_DID_TEXT_LIMIT}-character budget via {@link #normalizeC}, so it reads as a finished
	 * sentence inside its block.
	 *
	 * @param val raw paragraph text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitWhatWeDidText(String val) {
		return normalizeC(val, WHAT_WE_DID_TEXT_LIMIT);
	}

	/**
	 * Strips a single trailing period from a headline, leaving an ellipsis or any other punctuation alone.
	 *
	 * @param text the headline text
	 * @return the text without its trailing period
	 */
	String stripTrailingPeriod(String text) {
		return text.endsWith(".") && !text.endsWith("..") ? text.substring(0, text.length() - 1).trim() : text;
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
	 * over a hard cut, then falling back to the last word boundary (never mid-word) and rewriting the tail
	 * to end on a complete sentence (see {@link #finishSentence}) when no qualifying period is found.
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
				overview = finishSentence(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
			}
		}
		return overview;
	}

	/**
	 * Caps the Batch C recommendation title placeholder at 30 characters, preferring the last real
	 * sentence-ending {@code .} (decimal points excluded) past position 15 over a hard cut, then falling
	 * back to the last word boundary (never mid-word), with any trailing dangling comma stripped, when no
	 * qualifying period is found. Like a strategic point, the title is a headline and is not forced to end
	 * on a period.
	 *
	 * @param title raw recommendation-title text from the model (may be null)
	 * @return the trimmed title, or an empty string when {@code title} is null
	 */
	public String limitRecommendationTitle(String title) {
		if (title == null) {
			return "";
		}
		title = title.trim();
		if (title.length() > 30) {
			String cut = title.substring(0, 30);
			int threshold = 15;
			int lastPeriod = lastSentencePeriod(cut);
			if (lastPeriod > threshold) {
				title = title.substring(0, lastPeriod + 1).trim();
			} else {
				int ls = cut.lastIndexOf(' ');
				title = stripTrailingComma(ls > 0 ? cut.substring(0, ls).trim() : cut.trim());
			}
		}
		return title;
	}

	/**
	 * Normalizes the Batch C recommendation body copy with a 130-character budget via {@link #normalizeC},
	 * so it reads as a finished sentence ending on sentence-ending punctuation.
	 *
	 * @param val raw recommendation-text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitRecommendationText(String val) {
		return normalizeC(val, 130);
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
	 * Normalizes the {@code {{f_oppartunity}}} frequency-opportunity copy with a 180-character budget via
	 * {@link #normalizeC}.
	 *
	 * @param val raw frequency-opportunity text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitFOpportunity(String val) {
		return normalizeC(val, 180);
	}

	/**
	 * Normalizes the {@code {{f_fact}}} actual-frequency copy with a 140-character budget via
	 * {@link #normalizeC}.
	 *
	 * @param val raw actual-frequency text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitFFact(String val) {
		return normalizeC(val, 140);
	}

	/**
	 * Normalizes the {@code {{f_storytelling}}} frequency-storytelling copy with a 320-character budget via
	 * {@link #normalizeC}.
	 *
	 * @param val raw frequency-storytelling text from the model
	 * @return the normalized, length-capped text, or {@code null} when blank
	 */
	public String limitFStorytelling(String val) {
		return normalizeC(val, 320);
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

	/**
	 * Collapses whitespace in the primary-KPIs line, strips any surrounding quotes/backticks and a trailing
	 * period, and caps it at 60 characters, so the model's reply renders as a single clean KPI string.
	 *
	 * @param text raw primary-KPIs text from the model (may be null or blank)
	 * @return the cleaned, length-capped KPI line, or {@code null} when blank
	 */
	public String limitPrimaryKpis(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		text = text.replaceAll("\\s*[\\r\\n]+\\s*", " ").replaceAll("\\s{2,}", " ").trim();
		text = text.replaceAll("^[\"'`]+", "").trim();
		text = text.replaceAll("[\"'`.\\s]+$", "").trim();
		if (text.length() > 60) {
			text = text.substring(0, 60).trim();
			text = stripTrailingComma(text);
		}
		return text.isEmpty() ? null : text;
	}
}
