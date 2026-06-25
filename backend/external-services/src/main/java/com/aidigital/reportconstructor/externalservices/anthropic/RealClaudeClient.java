package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.Recommendation;
import com.aidigital.reportconstructor.service.reports.dto.StrategicInsight;
import com.aidigital.reportconstructor.service.reports.dto.TacticInsight;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Real Anthropic Messages API implementation — a faithful port of PHP
 * {@code claude_api.php} (batches A/B/C) plus {@code resolveGeoFromTab}, with an added Batch D
 * compression pass that asks Claude to shrink oversized placeholder text before the hard-truncation
 * safety net runs. Activated only when {@code ANTHROPIC_API_KEY} is set; otherwise
 * {@link StubClaudeClient} is the sole candidate.
 *
 * <p>Every batch returns the empty DTO on any error/timeout/parse failure,
 * exactly like the PHP functions return {@code []} — the resolvers then fall
 * back to manual/sheet values or {@code "—"}.
 */
@Component
@Primary
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class RealClaudeClient implements ClaudeClient {

	private static final int STRATEGIC_POINT_LIMIT = 22;
	private static final int STRATEGIC_OVERVIEW_LIMIT = 240;
	private static final int RESULTS_OVERVIEW_LIMIT = 380;
	private static final int THOUGHT_LIMIT = 220;
	private static final int TACTIC_OVERVIEW_LIMIT = 210;
	private static final int RECOMMENDATION_TITLE_LIMIT = 30;
	private static final int RECOMMENDATION_TEXT_LIMIT = 130;
	private static final int F_OPPORTUNITY_LIMIT = 180;
	private static final int F_FACT_LIMIT = 140;
	private static final int F_STORYTELLING_LIMIT = 320;

	private final AnthropicMessagesClient messagesClient;
	private final ClaudeBatchPromptBuilder promptBuilder;
	private final ClaudeResponseNormalizer normalizer;
	private final ClaudeCompressionService compressionService;
	private final ReportClaudeDefaults claudeDefaults;

	public RealClaudeClient(
			AnthropicMessagesClient messagesClient,
			ClaudeBatchPromptBuilder promptBuilder,
			ClaudeResponseNormalizer normalizer,
			ClaudeCompressionService compressionService,
			ReportClaudeDefaults claudeDefaults) {
		this.messagesClient = messagesClient;
		this.promptBuilder = promptBuilder;
		this.normalizer = normalizer;
		this.compressionService = compressionService;
		this.claudeDefaults = claudeDefaults;
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public ClaudeStrategic batchStrategic(CampaignData data, String brief) {
		var prompt = promptBuilder.buildBatchAPrompt(data, brief);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyStrategic();
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 2000, 60, "BatchA", false);
		if (parsed == null) {
			return claudeDefaults.emptyStrategic();
		}

		String age = normalizer.textOrNull(parsed.get("audience_age"));
		if (age != null) {
			age = age.replaceAll("\\s+", " ").trim();
			if ("not specified".equals(age.toLowerCase(Locale.ROOT))) {
				age = null;
			}
		}

		String seg = normalizer.limitAudienceSegments(normalizer.textOrNull(parsed.get("audience_segments")));
		String overview = normalizer.normalizeProposal(normalizer.textOrNull(parsed.get("proposal_overview")), 400);

		JsonNode arr = parsed.get("strategic_insights");
		List<ClaudeCompressionField> compressionFields = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			JsonNode item = (arr != null && arr.isArray() && i < arr.size()) ? arr.get(i) : null;
			String rawPoint = item == null ? "" : item.path("point").asText("").trim();
			String rawOverview = item == null ? "" : item.path("overview").asText("").trim();
			compressionFields.add(new ClaudeCompressionField("point_" + i, rawPoint, STRATEGIC_POINT_LIMIT));
			compressionFields.add(new ClaudeCompressionField("overview_" + i, rawOverview, STRATEGIC_OVERVIEW_LIMIT));
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Strategic");

		List<StrategicInsight> insights = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String point = normalizer.limitStrategicPoint(compressed.get("point_" + i));
			String ov = normalizer.limitStrategicOverview(compressed.get("overview_" + i));
			insights.add(new StrategicInsight(point, ov));
		}

		return new ClaudeStrategic(age, seg, overview, insights);
	}

	@Override
	public ClaudeTactical batchTactical(CampaignData data, String brief) {
		var prompt = promptBuilder.buildBatchBPrompt(data, brief);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyTactical();
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 500, 30, "BatchB", false);
		if (parsed == null) {
			return claudeDefaults.emptyTactical();
		}

		Map<Integer, TacticInsight> result = new LinkedHashMap<>();
		var fields = parsed.fields();
		while (fields.hasNext()) {
			var ent = fields.next();
			int n;
			try {
				n = Integer.parseInt(ent.getKey().trim());
			} catch (NumberFormatException ex) {
				continue;
			}
			JsonNode vals = ent.getValue();
			if (!data.tactics().containsKey(n) || vals == null || !vals.isObject()) {
				continue;
			}
			int male = Math.clamp(vals.path("male").asInt(50), 0, 100);
			int female = 100 - male;
			String weekdays = normalizer.textOrNull(vals.get("weekdays_peak"));
			String weekends = normalizer.textOrNull(vals.get("weekends_peak"));
			if (weekdays != null) {
				weekdays = weekdays.trim();
			}
			if (weekends != null) {
				weekends = weekends.trim();
			}
			result.put(n, new TacticInsight(male, female, weekdays, weekends));
		}
		return new ClaudeTactical(result);
	}

	@Override
	public ClaudeResults batchResults(CampaignData data, String brief, CampaignFrequencies frequencies) {
		var prompt = promptBuilder.buildBatchCPrompt(data, brief, frequencies);
		if (prompt.isEmpty()) {
			return claudeDefaults.emptyResults();
		}
		JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 3500, 60, "BatchC", true);
		if (parsed == null) {
			return claudeDefaults.emptyResults();
		}

		String rawResultsOverview = normalizer.textOrNull(parsed.get("results_overview"));
		List<String> rawThoughts =
				normalizer.normalizeThoughts(normalizer.textOrNull(parsed.get("thoughts_on_performance")));

		Map<Integer, String> rawTacticOverviews = new LinkedHashMap<>();
		JsonNode raw = parsed.get("tactic_overviews");
		if (raw != null && raw.isObject()) {
			var it = raw.fields();
			while (it.hasNext()) {
				var ent = it.next();
				int n;
				try {
					n = Integer.parseInt(ent.getKey().trim());
				} catch (NumberFormatException ex) {
					continue;
				}
				if (n <= 0) {
					continue;
				}
				rawTacticOverviews.put(n, ent.getValue().asText(""));
			}
		}

		JsonNode recArr = parsed.get("optimization_recommendations");
		String[] rawRecTitles = new String[4];
		String[] rawRecTexts = new String[4];
		for (int i = 0; i < 4; i++) {
			JsonNode item = (recArr != null && recArr.isArray() && i < recArr.size()) ? recArr.get(i) : null;
			rawRecTitles[i] = item == null ? "" : item.path("title").asText("").trim();
			rawRecTexts[i] = item == null ? "" : item.path("text").asText("").trim();
		}

		String rawFOpportunity = normalizer.textOrNull(parsed.get("f_opportunity"));
		String rawFFact = normalizer.textOrNull(parsed.get("f_fact"));
		String rawFStorytelling = normalizer.textOrNull(parsed.get("f_storytelling"));

		List<ClaudeCompressionField> compressionFields = new ArrayList<>();
		if (rawResultsOverview != null) {
			compressionFields.add(
					new ClaudeCompressionField("results_overview", rawResultsOverview, RESULTS_OVERVIEW_LIMIT));
		}
		if (rawFOpportunity != null) {
			compressionFields.add(new ClaudeCompressionField("f_opportunity", rawFOpportunity, F_OPPORTUNITY_LIMIT));
		}
		if (rawFFact != null) {
			compressionFields.add(new ClaudeCompressionField("f_fact", rawFFact, F_FACT_LIMIT));
		}
		if (rawFStorytelling != null) {
			compressionFields.add(new ClaudeCompressionField("f_storytelling", rawFStorytelling, F_STORYTELLING_LIMIT));
		}
		for (int i = 0; i < rawThoughts.size(); i++) {
			String thought = rawThoughts.get(i);
			if (thought != null) {
				compressionFields.add(new ClaudeCompressionField("thought_" + i, thought, THOUGHT_LIMIT));
			}
		}
		for (Map.Entry<Integer, String> e : rawTacticOverviews.entrySet()) {
			compressionFields.add(
					new ClaudeCompressionField("tactic_overview_" + e.getKey(), e.getValue(), TACTIC_OVERVIEW_LIMIT));
		}
		for (int i = 0; i < 4; i++) {
			compressionFields.add(
					new ClaudeCompressionField("rec_title_" + i, rawRecTitles[i], RECOMMENDATION_TITLE_LIMIT));
			compressionFields.add(
					new ClaudeCompressionField("rec_text_" + i, rawRecTexts[i], RECOMMENDATION_TEXT_LIMIT));
		}
		Map<String, String> compressed = compressionService.compress(compressionFields, "BatchD-Results");

		String resultsOverview = rawResultsOverview == null
				? null
				: normalizer.limitResultsOverview(compressed.get("results_overview"));

		List<String> thoughts = new ArrayList<>();
		for (int i = 0; i < rawThoughts.size(); i++) {
			String thought = rawThoughts.get(i);
			thoughts.add(thought == null ? null : normalizer.normalizeC(compressed.get("thought_" + i), THOUGHT_LIMIT));
		}

		Map<Integer, String> tacticOverviews = new LinkedHashMap<>();
		for (Integer tacticNumber : rawTacticOverviews.keySet()) {
			tacticOverviews.put(tacticNumber,
					normalizer.limitTacticOverview(compressed.get("tactic_overview_" + tacticNumber)));
		}

		List<Recommendation> recommendations = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String title = normalizer.limitRecommendationTitle(compressed.get("rec_title_" + i));
			String text = normalizer.limitRecommendationText(compressed.get("rec_text_" + i));
			recommendations.add(new Recommendation(title, text));
		}

		String fOpportunity = rawFOpportunity == null
				? null
				: normalizer.limitFOpportunity(compressed.get("f_opportunity"));
		String fFact = rawFFact == null ? null : normalizer.limitFFact(compressed.get("f_fact"));
		String fStorytelling = rawFStorytelling == null
				? null
				: normalizer.limitFStorytelling(compressed.get("f_storytelling"));

		return new ClaudeResults(resultsOverview, thoughts, tacticOverviews, recommendations,
				fOpportunity, fFact, fStorytelling);
	}

	@Override
	public String summarizeGeo(List<List<String>> geoRows) {
		if (geoRows == null || geoRows.isEmpty()) {
			return null;
		}
		String prompt = promptBuilder.buildGeoPrompt(geoRows);
		JsonNode resp = messagesClient.callRaw(prompt, 60, 30, "Geo");
		if (resp == null) {
			return null;
		}
		return normalizer.limitGeoSummary(normalizer.extractText(resp));
	}
}
