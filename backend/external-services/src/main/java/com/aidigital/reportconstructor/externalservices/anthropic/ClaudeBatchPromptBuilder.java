package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.dto.Totals;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds Anthropic Messages API prompts and campaign context blocks for Claude batches A/B/C and geo.
 */
@Component
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class ClaudeBatchPromptBuilder {

	private final ClaudeResponseNormalizer normalizer;
	private final Fmt fmt;

	public ClaudeBatchPromptBuilder(ClaudeResponseNormalizer normalizer, Fmt fmt) {
		this.normalizer = normalizer;
		this.fmt = fmt;
	}

	/**
	 * Builds the Batch A (strategic) prompt, or empty when there is no campaign context to send.
	 *
	 * @param data  parsed campaign plan and per-tactic performance used to assemble the brief/plan/tactic context
	 *                 blocks
	 * @param brief free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated as empty
	 *                when null)
	 * @return the Batch A strategic prompt requesting audience/proposal/insights JSON, or empty when no context block
	 * could be built
	 */
	public Optional<String> buildBatchAPrompt(CampaignData data, String brief) {
		String brf = brief == null ? "" : brief;
		List<String> planLines = new ArrayList<>();
		if (normalizer.notBlank(data.client())) {
			planLines.add("Client:       " + data.client());
		}
		if (normalizer.notBlank(data.campaign())) {
			planLines.add("Campaign:     " + data.campaign());
		}
		if (normalizer.notBlank(data.geo())) {
			planLines.add("Geo:          " + data.geo());
		}
		if (normalizer.notBlank(data.goal())) {
			planLines.add("Goal:         " + data.goal());
		}
		if (normalizer.notBlank(data.flightDates())) {
			planLines.add("Flight:       " + data.flightDates());
		}
		if (normalizer.notBlank(data.budget())) {
			planLines.add("Budget:       " + data.budget());
		}
		if (normalizer.notBlank(data.primaryKpis())) {
			planLines.add("KPIs:         " + data.primaryKpis());
		}
		if (normalizer.notBlank(data.tacticsList())) {
			planLines.add("Tactics:      " + data.tacticsList());
		}
		if (normalizer.notBlank(data.audienceAge())) {
			planLines.add("Audience age: " + data.audienceAge());
		}

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + " — " + t.name() + ":");
			if (t.spend() > 0) {
				line.append(" Spend $").append(fmt.intGroup(Math.round(t.spend())));
			}
			if (t.imps() > 0) {
				line.append(" | Imps ").append(fmt.intGroup(t.imps()));
			}
			if (t.ctr() != null) {
				line.append(" | CTR ").append(fmt.dec2(t.ctr())).append('%');
			}
			if (t.vcr() != null) {
				line.append(" | VCR ").append(fmt.dec2(t.vcr())).append('%');
			}
			tacticLines.add(line.toString());
		}

		List<String> ctx = new ArrayList<>();
		if (!brf.isEmpty()) {
			ctx.add("=== CAMPAIGN BRIEF ===\n" + brf);
		}
		if (!planLines.isEmpty()) {
			ctx.add("=== CAMPAIGN PLAN ===\n" + String.join("\n", planLines));
		}
		if (!tacticLines.isEmpty()) {
			ctx.add("=== TACTIC PERFORMANCE ===\n" + String.join("\n", tacticLines));
		}
		if (normalizer.notBlank(data.audienceTab())) {
			ctx.add("=== AUDIENCE & INVENTORY TAB ===\n" + data.audienceTab());
		}
		if (ctx.isEmpty()) {
			return Optional.empty();
		}
		String context = String.join("\n\n", ctx);

		String prompt =
				"You are a senior digital media strategist at an advertising agency writing a client-facing campaign " +
						"report.\n\n"
						+ "ANALYTICAL PRINCIPLES — apply to every text field you generate:\n"
						+ "1. INTERPRET, NEVER ENUMERATE. Every metric must answer \"What does this mean for the " +
						"campaign?\" "
						+ "Raw data repeated as prose is not analysis. Transform each data point into a business " +
						"implication.\n"
						+ "2. NO GENERIC LANGUAGE. Every sentence must be specific to this campaign's data. "
						+ "Forbidden phrases: \"performance is tracking well\", \"results are in line with " +
						"expectations\", "
						+ "\"we recommend monitoring\", \"this tactic requires further optimization\". "
						+ "If a sentence could appear in any other campaign report unchanged — rewrite it.\n"
						+ "3. EXPLAIN THE WHY. Don't write \"X had a high CTR.\" Write WHY: creative format, placement" +
						" type, "
						+ "audience intent level, message-to-moment alignment, competitive bid landscape, etc.\n"
						+ "4. SPECIFICITY IS MANDATORY. Name the specific tactic, channel, audience segment, or geo. "
						+ "Name the specific cause. Name the specific action or outcome.\n\n"
						+ "Read the campaign data below and return a JSON object with EXACTLY these keys:\n\n"
						+ "{\n"
						+ "  \"audience_age\": string,        // target audience age, e.g. \"25-44 years old\" or " +
						"\"35+\". "
						+ "Exact range if stated; lower bound only if a floor; generation → range (Millennials=25-40, "
						+ "GenZ=18-27, GenX=41-56, Boomers=57-75); null if not specified.\n"
						+ "  \"audience_segments\": string,   // ≤80 chars. WHO is targeted — natural phrase like "
						+ "\"Affluent auto-intenders, HHI $100K+\". No platforms/budgets/KPIs. null if no info.\n"
						+ "  \"proposal_overview\": string,   // Exactly 2 complete sentences. Past tense, no line " +
						"breaks, no bullets. "
						+ "Sentence 1: why the campaign ran — client objective + target audience. "
						+ "Sentence 2: how it ran — tactic mix + geo + flight period. "
						+ "Name the actual tactics, actual audience, actual geo. No character limit — write both " +
						"sentences completely.\n"
						+ "  \"strategic_insights\": array    // Exactly 4 objects: {\"point\": string, \"overview\": " +
						"string}.\n"
						+ "                                // CRITICAL for 'point': MAX 20 CHARACTERS ABSOLUTE HARD " +
						"LIMIT.\n"
						+ "                                // For 'overview': MAX 230 CHARACTERS.\n"
						+ "                                // Each overview = strategic intention/approach + WHY this " +
						"choice made sense "
						+ "for THIS client/campaign. Unique angles, past tense, Business English. No filler.\n"
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- null for any field where there is genuinely no data.\n"
						+ "- Do NOT invent facts. Base everything strictly on the provided data.\n"
						+ "- Output in English regardless of input language.\n\n"
						+ "Campaign data:\n" + context;
		return Optional.of(prompt);
	}

	/**
	 * Builds the Batch B (tactical) prompt, or empty when there are no tactics.
	 *
	 * @param data  parsed campaign data; its tactic map drives the per-tactic gender/peak-time estimation and the
	 *                 required JSON keys
	 * @param brief free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated as empty
	 *                when null)
	 * @return the Batch B prompt requesting per-tactic gender split and weekday/weekend peak windows, or empty when
	 * no tactics exist
	 */
	public Optional<String> buildBatchBPrompt(CampaignData data, String brief) {
		if (data.tactics() == null || data.tactics().isEmpty()) {
			return Optional.empty();
		}
		String brf = brief == null ? "" : brief;

		List<String> contextLines = new ArrayList<>();
		if (normalizer.notBlank(data.client())) {
			contextLines.add("Client:   " + data.client());
		}
		if (normalizer.notBlank(data.campaign())) {
			contextLines.add("Campaign: " + data.campaign());
		}
		if (normalizer.notBlank(data.geo())) {
			contextLines.add("Geo:      " + data.geo());
		}
		if (normalizer.notBlank(data.goal())) {
			contextLines.add("Goal:     " + data.goal());
		}
		if (normalizer.notBlank(data.primaryKpis())) {
			contextLines.add("KPIs:     " + data.primaryKpis());
		}
		if (normalizer.notBlank(data.audienceAge())) {
			contextLines.add("Audience age: " + data.audienceAge());
		}

		StringBuilder contextBlock = new StringBuilder();
		if (!brf.isEmpty()) {
			contextBlock.append("=== CAMPAIGN BRIEF ===\n").append(brf).append("\n\n");
		}
		if (!contextLines.isEmpty()) {
			contextBlock.append("=== CAMPAIGN CONTEXT ===\n")
					.append(String.join("\n", contextLines)).append("\n\n");
		}

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + ": " + t.name());
			if (t.imps() > 0) {
				line.append(" (").append(fmt.intGroup(t.imps())).append(" imps)");
			}
			tacticLines.add(line.toString());
		}

		List<String> keys = new ArrayList<>();
		for (Integer k : data.tactics().keySet()) {
			keys.add("\"" + k + "\"");
		}
		String tacticKeys = "[" + String.join(",", keys) + "]";

		String prompt =
				contextBlock
						+ "You are a digital media analyst. For each tactic below, estimate:\n"
						+ "1. Gender split of the reached audience.\n"
						+ "2. Peak impression time window on WEEKDAYS (format: \"H AM/PM – H AM/PM\", e.g. \"7 PM – 11" +
						" PM\").\n"
						+ "3. Peak impression time window on WEEKENDS (same format).\n\n"
						+ "Tactics:\n" + String.join("\n", tacticLines) + "\n\n"
						+ "Rules:\n"
						+ "1. Return ONLY a valid JSON object — no markdown, no backticks.\n"
						+ "2. Keys are tactic numbers as strings: " + tacticKeys + "\n"
						+ "3. Each value: {\"male\": int, \"female\": int, \"weekdays_peak\": \"H AM/PM – H AM/PM\", " +
						"\"weekends_peak\": \"H AM/PM – H AM/PM\"}\n"
						+ "4. male + female = 100. All integers.\n"
						+ "5. Gender: use campaign context as primary signal. Avoid defaulting to 50/50.\n"
						+ "6. CRITICAL: Never use multiples of 5 for gender. Use uneven integers like 43,57,61,38.\n"
						+ "7. Peak windows: whole hours, 2–5 hour range. Format: \"H PM – H PM\" (no leading zeros)" +
						".\n\n"
						+ "Example: {\"1\": {\"male\": 38, \"female\": 62, \"weekdays_peak\": \"7 PM – 11 PM\", " +
						"\"weekends_peak\": \"10 AM – 2 PM\"}}";
		return Optional.of(prompt);
	}

	/**
	 * Builds the Batch C (results) prompt, or empty when there is no campaign context to send.
	 *
	 * @param data  parsed campaign data including overall totals and actual-vs-plan tactic metrics used for the
	 *                 results context blocks
	 * @param brief free-text campaign brief prepended as the {@code === CAMPAIGN BRIEF ===} section (treated as empty
	 *                when null)
	 * @return the Batch C post-campaign prompt requesting results overview, performance thoughts and per-tactic
	 * overviews JSON, or empty when no context block could be built
	 */
	public Optional<String> buildBatchCPrompt(CampaignData data, String brief) {
		String brf = brief == null ? "" : brief;

		List<String> planLines = new ArrayList<>();
		if (normalizer.notBlank(data.client())) {
			planLines.add("Client:   " + data.client());
		}
		if (normalizer.notBlank(data.campaign())) {
			planLines.add("Campaign: " + data.campaign());
		}
		if (normalizer.notBlank(data.flightDates())) {
			planLines.add("Flight:   " + data.flightDates());
		}
		if (normalizer.notBlank(data.goal())) {
			planLines.add("Goal:     " + data.goal());
		}
		if (normalizer.notBlank(data.budget())) {
			planLines.add("Budget:   " + data.budget());
		}

		Totals tot = data.totals();
		List<String> totalLines = new ArrayList<>();
		if (tot != null) {
			if (tot.spend() > 0) {
				totalLines.add("Total Spend: $" + fmt.intGroup(Math.round(tot.spend())));
			}
			if (tot.imps() > 0) {
				totalLines.add("Total Imps:  " + fmt.intGroup(tot.imps()));
			}
			if (tot.ctr() != null) {
				totalLines.add("Total CTR:   " + fmt.dec2(tot.ctr()) + "%");
			}
			if (tot.vcr() != null) {
				totalLines.add("Total VCR:   " + fmt.dec2(tot.vcr()) + "%");
			}
		}

		List<String> tacticLines = new ArrayList<>();
		for (Map.Entry<Integer, Tactic> e : data.tactics().entrySet()) {
			Tactic t = e.getValue();
			StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + " — " + t.name() + ":");
			if (t.spend() > 0) {
				line.append(" Actual Spend $").append(fmt.intGroup(Math.round(t.spend())));
			}
			if (t.imps() > 0) {
				line.append(" | Actual Imps ").append(fmt.intGroup(t.imps()));
			}
			if (t.ctr() != null) {
				line.append(" | Actual CTR ").append(fmt.dec2(t.ctr())).append('%');
			}
			if (t.vcr() != null) {
				line.append(" | Actual VCR ").append(fmt.dec2(t.vcr())).append('%');
			}
			if (t.planSpend() != null) {
				line.append(" | Plan Spend $").append(fmt.intGroup(Math.round(t.planSpend())));
			}
			if (t.planImps() != null) {
				line.append(" | Plan Imps ").append(fmt.intGroup(t.planImps()));
			}
			if (t.planCtr() != null) {
				line.append(" | Plan CTR ").append(fmt.dec2(t.planCtr())).append('%');
			}
			if (t.planVcr() != null) {
				line.append(" | Plan VCR ").append(fmt.dec2(t.planVcr())).append('%');
			}
			tacticLines.add(line.toString());
		}

		List<String> ctx = new ArrayList<>();
		if (!brf.isEmpty()) {
			ctx.add("=== CAMPAIGN BRIEF ===\n" + brf);
		}
		if (!planLines.isEmpty()) {
			ctx.add("=== CAMPAIGN PLAN ===\n" + String.join("\n", planLines));
		}
		if (!totalLines.isEmpty()) {
			ctx.add("=== OVERALL RESULTS ===\n" + String.join("\n", totalLines));
		}
		if (!tacticLines.isEmpty()) {
			ctx.add("=== RESULTS BY TACTIC ===\n" + String.join("\n", tacticLines));
		}
		if (ctx.isEmpty()) {
			return Optional.empty();
		}
		String context = String.join("\n\n", ctx);

		List<String> nums = new ArrayList<>();
		for (Integer k : data.tactics().keySet()) {
			nums.add(String.valueOf(k));
		}
		String tacticNums = String.join(", ", nums);

		String prompt =
				"You are a senior digital media analyst writing a post-campaign report for a client presentation.\n\n"
						+ "ANALYTICAL PRINCIPLES — non-negotiable, apply to every text field:\n"
						+ "1. OBSERVATION → EXPLANATION → RECOMMENDATION in every insight. "
						+ "State what happened, explain WHY it happened (name a specific cause: learning phase, " +
						"creative fatigue, "
						+ "inventory constraints, bid competitiveness, audience saturation, pacing decision, seasonal " +
						"effect, etc.), "
						+ "then state what this means or what should follow. All three parts are mandatory.\n"
						+ "2. INTERPRET, NEVER ENUMERATE. The reader can see the numbers. Your job is to explain what " +
						"they mean. "
						+ "WRONG: \"CTV delivered 12M impressions, the highest of any channel.\" "
						+ "RIGHT: \"CTV absorbed the majority of delivery because it was the only channel with priced " +
						"inventory "
						+ "in the target geo during the campaign window — not a sign that other tactics " +
						"underperformed, "
						+ "but a feature of the buy structure.\" Transform every data point into a business " +
						"implication.\n"
						+ "3. 'SO WHAT' IS MANDATORY. Every metric cited must be followed by its business " +
						"consequence: "
						+ "did we hit goals? what does over/underdelivery mean for the brand? what did the audience " +
						"actually receive?\n"
						+ "4. NO GENERIC LANGUAGE. Forbidden: \"performance is tracking well\", \"results are in line " +
						"with expectations\", "
						+ "\"we recommend monitoring\", \"this tactic requires optimization\". "
						+ "Every sentence must be specific to THIS campaign's numbers, channels, and audience.\n"
						+ "5. NAME THE CAUSE. Don't say performance was strong — say why: audience targeting " +
						"precision, "
						+ "creative format fit, placement quality, flight timing, competitive dynamics, etc.\n\n"
						+ "Read the campaign data and return a JSON object with EXACTLY these keys:\n\n"
						+ "{\n"
						+ "  \"results_overview\": string,        // EXACTLY 2 SENTENCES. Past tense, no bullets, no " +
						"line breaks. Hard limit: ≤380 chars total.\n"
						+ "  //  SENTENCE 1 — Overall result + key metric vs plan + reason WHY performance was as it " +
						"was.\n"
						+ "  //    Must include: the most significant delivery outcome (over/underdelivery vs plan) + " +
						"one specific cause\n"
						+ "  //    (budget pacing, inventory constraints, audience fit, bid dynamics, flight timing, " +
						"etc.).\n"
						+ "  //    WRONG: \"The campaign delivered X impressions across Y tactics.\"\n"
						+ "  //    RIGHT: \"The campaign significantly underdelivered against planned reach goals due " +
						"to budget pacing delays,\n"
						+ "  //            achieving only 4.8M impressions versus a planned 13M+ through Q1 spending " +
						"constraints.\"\n"
						+ "  //  SENTENCE 2 — Tactic-level breakdown: which tactic(s) led performance and which " +
						"lagged, with a specific reason for each.\n"
						+ "  //    Name the actual tactics. Include one metric per tactic (VCR%, CTR%, imps, spend). " +
						"Name the cause.\n"
						+ "  //    RIGHT: \"Video tactics dominated actual delivery with strong completion rates (97% " +
						"for Live Sports, 98% for CTV/Netflix),\n"
						+ "  //            while display formats like DOOH underperformed due to limited spend " +
						"activation.\"\n"
						+ "  //  DO NOT write a third sentence. Stop after the second.\n"
						+ "  //  CLIENT-FACING TONE: This text goes directly into a client presentation.\n"
						+ "  //    Always lead with what went well or what was achieved — even in underdelivery " +
						"scenarios, frame it as\n"
						+ "  //    a strategic constraint, not a failure. Highlight strong metrics (VCR, CTR, " +
						"completion rates)\n"
						+ "  //    before mentioning gaps. If a tactic underperformed, attribute it to external " +
						"factors\n"
						+ "  //    (inventory availability, budget pacing, market conditions) — never to poor " +
						"execution.\n"
						+ "  \"thoughts_on_performance\": string,  // EXACTLY 4 SHORT ANALYTICAL PARAGRAPHS separated " +
						"by the literal string \" | \".\n"
						+ "  //  Each paragraph: 1–2 sentences, past tense, client-friendly. NOT bullet headers — " +
						"flowing sentences.\n"
						+ "  //  REQUIRED STRUCTURE — exactly these 4 paragraphs in this order:\n"
						+ "  //  (1) Which tactic/channel performed best and the specific reason WHY (not just 'it " +
						"performed well').\n"
						+ "  //  (2) Why the campaign succeeded overall — name the mechanism: targeting precision, " +
						"audience-channel fit, creative alignment, etc.\n"
						+ "  //  (3) One creative or format insight — what worked and why (format size, video length, " +
						"placement position, etc.).\n"
						+ "  //  (4) Efficiency or reach insight — what the spend delivered beyond raw impressions " +
						"(CPM efficiency, frequency management, reach quality).\n"
						+ "  //  CRITICAL: produce EXACTLY 4 paragraphs — no more, no fewer. Result must contain " +
						"EXACTLY 3 \" | \" separators.\n"
						+ "  //  BAD example: \"Programmatic video performed well.\" | \"Audience targeting was " +
						"effective.\" | ...\n"
						+ "  //  GOOD example: \"Programmatic video exceeded impression goals by 0.6%, driven by " +
						"strong inventory availability "
						+ "in the 25-44 demo during evening dayparts — the format's native environment for this " +
						"audience.\" | ...\n"
						+ "  //  Total string including \" | \" separators must be ≤700 chars.\n"
						+ "  \"tactic_overviews\": {               // Per-tactic. Keys: tactic numbers as strings (" + tacticNums + ")\n"
						+ "    \"N\": string                        // MAX 190 CHARACTERS. End on a complete word and " +
						"sentence.\n"
						+ "  //  STRUCTURE: [What the tactic delivered vs plan] + [WHY it performed as it did] + " +
						"[business So what].\n"
						+ "  //  All three parts required even in 190 chars — be concise but complete.\n"
						+ "  //  WRONG: \"CTV delivered 5M impressions at 98% VCR, exceeding plan.\"\n"
						+ "  //  RIGHT: \"CTV delivered 5M impressions at 98% VCR (+2pp vs plan), driven by premium " +
						"inventory selection — "
						+ "confirming the audience's high receptivity to full-screen video in this vertical.\"\n"
						+ "  //  Focus metrics by tactic type: Display→Imps+CTR; Video/Pre-roll→Imps+CTR+VCR; " +
						"CTV/OTT→Imps+VCR; Audio→Completions.\n"
						+ "  //  Past tense. No bullets. Business English. Max 2 sentences.\n"
						+ "  }\n"
						+ "}\n\n"
						+ "Rules:\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
						+ "- null for results_overview / thoughts_on_performance if genuinely insufficient data.\n"
						+ "- For tactic_overviews: include a key for every tactic number listed above.\n"
						+ "- Do NOT invent metrics. Use only the numbers provided.\n"
						+ "- CRITICAL: each tactic_overview value MUST end on a complete word/sentence and be ≤190 " +
						"characters.\n"
						+ "- thoughts_on_performance uses \" | \" (space-pipe-space) as paragraph separator — NOT " +
						"newlines.\n"
						+ "- DEPTH OVER BREADTH: one insight with a real explanation beats three that only restate " +
						"numbers.\n"
						+ "- Output in English.\n\n"
						+ "Campaign data:\n" + context;
		return Optional.of(prompt);
	}

	/**
	 * Builds the Batch D (compression) prompt asking Claude to shrink each oversized field to its character
	 * budget while preserving meaning, or empty when there are no fields to compress.
	 *
	 * @param fields oversized fields to compress, each carrying its own raw text and character budget
	 * @return the compression prompt requesting a JSON object keyed by each field's {@code key}, or empty when
	 * {@code fields} is empty
	 */
	public Optional<String> buildCompressionPrompt(List<ClaudeCompressionField> fields) {
		if (fields.isEmpty()) {
			return Optional.empty();
		}
		List<String> entries = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (ClaudeCompressionField field : fields) {
			keys.add("\"" + field.key() + "\"");
			entries.add("- key: \"" + field.key() + "\", limit: " + field.maxChars() + " characters\n"
					+ "  text: \"" + field.text().replace("\"", "'") + "\"");
		}
		return Optional.of(
				"You are editing client-facing campaign-report copy that is too long for its layout slot.\n\n"
						+ "For EACH field below, rewrite the text so it fits within its character limit, while " +
						"preserving its key meaning and business message as closely as possible. Cut secondary " +
						"detail before cutting the main point. Always end on a complete word and, where the limit " +
						"allows, a complete sentence — never cut off mid-word or mid-clause.\n\n"
						+ "Fields:\n" + String.join("\n", entries) + "\n\n"
						+ "Return ONLY a JSON object mapping each key to its rewritten text, with no other keys:\n"
						+ "{" + String.join(", ", keys) + "}\n\n"
						+ "Rules:\n"
						+ "- Every value's length MUST be at or under that field's character limit — count " +
						"characters, not words.\n"
						+ "- Keep the same language (English) and tense as the original text.\n"
						+ "- Return ONLY the JSON object — no markdown, no backticks, no explanation."
		);
	}

	/**
	 * Builds the geo-tab summarisation prompt from spreadsheet rows.
	 *
	 * @param geoRows rows of the media-plan "Geo" tab; each inner list is one row whose cells are joined with {@code
	 * " | "} (null rows are skipped)
	 * @return a prompt asking Claude to condense the listed locations into a single short comma-separated string of
	 * key regions
	 */
	public String buildGeoPrompt(List<List<String>> geoRows) {
		StringBuilder tab = new StringBuilder();
		for (List<String> row : geoRows) {
			if (row == null) {
				continue;
			}
			tab.append(String.join(" | ", row)).append('\n');
		}
		return "Below is a 'Geo' tab from a media plan listing geographic targeting locations.\n"
				+ "Summarise the locations into a single short comma-separated string (≤40 characters), "
				+ "naming the most important regions/cities/states. No explanation — return only the string.\n\n"
				+ tab;
	}
}
