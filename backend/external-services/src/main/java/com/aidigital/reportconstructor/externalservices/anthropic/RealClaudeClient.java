package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
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
 * {@code claude_api.php} (batches A/B/C) plus {@code resolveGeoFromTab}.
 * Activated only when {@code ANTHROPIC_API_KEY} is set; otherwise
 * {@link StubClaudeClient} is the sole candidate.
 *
 * <p>Every batch returns the empty DTO on any error/timeout/parse failure,
 * exactly like the PHP functions return {@code []} — the resolvers then fall
 * back to manual/sheet values or {@code "—"}.
 */
@Slf4j
@Component
@Primary
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class RealClaudeClient implements ClaudeClient {

    private final AnthropicMessagesClient messagesClient;
    private final ClaudeBatchPromptBuilder promptBuilder;
    private final ClaudeResponseNormalizer normalizer;
    private final ReportClaudeDefaults claudeDefaults;

    public RealClaudeClient(
            AnthropicMessagesClient messagesClient,
            ClaudeBatchPromptBuilder promptBuilder,
            ClaudeResponseNormalizer normalizer,
            ReportClaudeDefaults claudeDefaults) {
        this.messagesClient = messagesClient;
        this.promptBuilder = promptBuilder;
        this.normalizer = normalizer;
        this.claudeDefaults = claudeDefaults;
        log.info("[claude] live Anthropic client initialised");
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

        List<ClaudeStrategic.StrategicInsight> insights = new ArrayList<>();
        JsonNode arr = parsed.get("strategic_insights");
        if (arr != null && arr.isArray()) {
            for (int i = 0; i < arr.size() && i < 4; i++) {
                JsonNode item = arr.get(i);
                String point = normalizer.limitStrategicPoint(item.path("point").asText(""));
                String ov = normalizer.limitStrategicOverview(item.path("overview").asText(""));
                insights.add(new ClaudeStrategic.StrategicInsight(point, ov));
            }
        }
        while (insights.size() < 4) {
            insights.add(new ClaudeStrategic.StrategicInsight("", ""));
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

        Map<Integer, ClaudeTactical.TacticInsight> result = new LinkedHashMap<>();
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
            int male = Math.max(0, Math.min(100, vals.path("male").asInt(50)));
            int female = 100 - male;
            String weekdays = normalizer.textOrNull(vals.get("weekdays_peak"));
            String weekends = normalizer.textOrNull(vals.get("weekends_peak"));
            if (weekdays != null) {
                weekdays = weekdays.trim();
            }
            if (weekends != null) {
                weekends = weekends.trim();
            }
            result.put(n, new ClaudeTactical.TacticInsight(male, female, weekdays, weekends));
        }
        return new ClaudeTactical(result);
    }

    @Override
    public ClaudeResults batchResults(CampaignData data, String brief) {
        var prompt = promptBuilder.buildBatchCPrompt(data, brief);
        if (prompt.isEmpty()) {
            return claudeDefaults.emptyResults();
        }
        JsonNode parsed = messagesClient.callJsonObject(prompt.get(), 3500, 60, "BatchC", true);
        if (parsed == null) {
            return claudeDefaults.emptyResults();
        }

        String resultsOverview = normalizer.limitResultsOverview(normalizer.textOrNull(parsed.get("results_overview")));
        List<String> thoughts = normalizer.normalizeThoughts(normalizer.textOrNull(parsed.get("thoughts_on_performance")));

        Map<Integer, String> tacticOverviews = new LinkedHashMap<>();
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
                tacticOverviews.put(n, normalizer.limitTacticOverview(ent.getValue().asText("")));
            }
        }
        return new ClaudeResults(resultsOverview, thoughts, tacticOverviews);
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
