package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.engine.Fmt;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String VERSION = "2023-06-01";
    private static final Pattern FENCE_OPEN = Pattern.compile("^```(?:json)?\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE_CLOSE = Pattern.compile("\\s*```$");

    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final ClaudeResponseNormalizer normalizer;
    private final ReportClaudeDefaults claudeDefaults;
    private final Fmt fmt;

    public RealClaudeClient(
            AnthropicProperties props,
            ClaudeResponseNormalizer normalizer,
            ReportClaudeDefaults claudeDefaults,
            Fmt fmt) {
        this.apiKey = props.getApiKey();
        this.model = props.getModel();
        this.normalizer = normalizer;
        this.claudeDefaults = claudeDefaults;
        this.fmt = fmt;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        log.info("[claude] live Anthropic client initialised (model={})", model);
    }

    @Override
    public boolean isLive() {
        return true;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BATCH A — strategic
    // ════════════════════════════════════════════════════════════════════════
    @Override
    public ClaudeStrategic batchStrategic(CampaignData data, String brief) {
        String brf = brief == null ? "" : brief;
        List<String> planLines = new ArrayList<>();
        if (normalizer.notBlank(data.client()))       planLines.add("Client:       " + data.client());
        if (normalizer.notBlank(data.campaign()))     planLines.add("Campaign:     " + data.campaign());
        if (normalizer.notBlank(data.geo()))          planLines.add("Geo:          " + data.geo());
        if (normalizer.notBlank(data.goal()))         planLines.add("Goal:         " + data.goal());
        if (normalizer.notBlank(data.flightDates()))  planLines.add("Flight:       " + data.flightDates());
        if (normalizer.notBlank(data.budget()))       planLines.add("Budget:       " + data.budget());
        if (normalizer.notBlank(data.primaryKpis()))  planLines.add("KPIs:         " + data.primaryKpis());
        if (normalizer.notBlank(data.tacticsList()))  planLines.add("Tactics:      " + data.tacticsList());
        if (normalizer.notBlank(data.audienceAge()))  planLines.add("Audience age: " + data.audienceAge());

        List<String> tacticLines = new ArrayList<>();
        for (Map.Entry<Integer, CampaignData.Tactic> e : data.tactics().entrySet()) {
            CampaignData.Tactic t = e.getValue();
            StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + " — " + t.name() + ":");
            if (t.spend() > 0)      line.append(" Spend $").append(fmt.intGroup(Math.round(t.spend())));
            if (t.imps() > 0)       line.append(" | Imps ").append(fmt.intGroup(t.imps()));
            if (t.ctr() != null)    line.append(" | CTR ").append(fmt.dec2(t.ctr())).append('%');
            if (t.vcr() != null)    line.append(" | VCR ").append(fmt.dec2(t.vcr())).append('%');
            tacticLines.add(line.toString());
        }

        List<String> ctx = new ArrayList<>();
        if (!brf.isEmpty())            ctx.add("=== CAMPAIGN BRIEF ===\n" + brf);
        if (!planLines.isEmpty())      ctx.add("=== CAMPAIGN PLAN ===\n" + String.join("\n", planLines));
        if (!tacticLines.isEmpty())    ctx.add("=== TACTIC PERFORMANCE ===\n" + String.join("\n", tacticLines));
        if (normalizer.notBlank(data.audienceTab())) ctx.add("=== AUDIENCE & INVENTORY TAB ===\n" + data.audienceTab());
        if (ctx.isEmpty()) return claudeDefaults.emptyStrategic();
        String context = String.join("\n\n", ctx);

        String prompt =
            "You are a senior digital media strategist at an advertising agency writing a client-facing campaign report.\n\n"
          + "ANALYTICAL PRINCIPLES — apply to every text field you generate:\n"
          + "1. INTERPRET, NEVER ENUMERATE. Every metric must answer \"What does this mean for the campaign?\" "
          +    "Raw data repeated as prose is not analysis. Transform each data point into a business implication.\n"
          + "2. NO GENERIC LANGUAGE. Every sentence must be specific to this campaign's data. "
          +    "Forbidden phrases: \"performance is tracking well\", \"results are in line with expectations\", "
          +    "\"we recommend monitoring\", \"this tactic requires further optimization\". "
          +    "If a sentence could appear in any other campaign report unchanged — rewrite it.\n"
          + "3. EXPLAIN THE WHY. Don't write \"X had a high CTR.\" Write WHY: creative format, placement type, "
          +    "audience intent level, message-to-moment alignment, competitive bid landscape, etc.\n"
          + "4. SPECIFICITY IS MANDATORY. Name the specific tactic, channel, audience segment, or geo. "
          +    "Name the specific cause. Name the specific action or outcome.\n\n"
          + "Read the campaign data below and return a JSON object with EXACTLY these keys:\n\n"
          + "{\n"
          + "  \"audience_age\": string,        // target audience age, e.g. \"25-44 years old\" or \"35+\". "
          +    "Exact range if stated; lower bound only if a floor; generation → range (Millennials=25-40, "
          +    "GenZ=18-27, GenX=41-56, Boomers=57-75); null if not specified.\n"
          + "  \"audience_segments\": string,   // ≤80 chars. WHO is targeted — natural phrase like "
          +    "\"Affluent auto-intenders, HHI $100K+\". No platforms/budgets/KPIs. null if no info.\n"
          + "  \"proposal_overview\": string,   // Exactly 2 complete sentences. Past tense, no line breaks, no bullets. "
          +    "Sentence 1: why the campaign ran — client objective + target audience. "
          +    "Sentence 2: how it ran — tactic mix + geo + flight period. "
          +    "Name the actual tactics, actual audience, actual geo. No character limit — write both sentences completely.\n"
          + "  \"strategic_insights\": array    // Exactly 4 objects: {\"point\": string, \"overview\": string}.\n"
          +    "                                // CRITICAL for 'point': MAX 20 CHARACTERS ABSOLUTE HARD LIMIT.\n"
          +    "                                // For 'overview': MAX 230 CHARACTERS.\n"
          +    "                                // Each overview = strategic intention/approach + WHY this choice made sense "
          +    "for THIS client/campaign. Unique angles, past tense, Business English. No filler.\n"
          + "}\n\n"
          + "Rules:\n"
          + "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
          + "- null for any field where there is genuinely no data.\n"
          + "- Do NOT invent facts. Base everything strictly on the provided data.\n"
          + "- Output in English regardless of input language.\n\n"
          + "Campaign data:\n" + context;

        JsonNode parsed = call(prompt, 2000, 60, "BatchA", false);
        if (parsed == null) return claudeDefaults.emptyStrategic();

        String age = normalizer.textOrNull(parsed.get("audience_age"));
        if (age != null) {
            age = age.replaceAll("\\s+", " ").trim();
            if ("not specified".equals(age.toLowerCase(Locale.ROOT))) age = null;
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
        while (insights.size() < 4) insights.add(new ClaudeStrategic.StrategicInsight("", ""));

        return new ClaudeStrategic(age, seg, overview, insights);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BATCH B — tactical (gender split + peak windows)
    // ════════════════════════════════════════════════════════════════════════
    @Override
    public ClaudeTactical batchTactical(CampaignData data, String brief) {
        if (data.tactics() == null || data.tactics().isEmpty()) return claudeDefaults.emptyTactical();
        String brf = brief == null ? "" : brief;

        List<String> contextLines = new ArrayList<>();
        if (normalizer.notBlank(data.client()))      contextLines.add("Client:   " + data.client());
        if (normalizer.notBlank(data.campaign()))    contextLines.add("Campaign: " + data.campaign());
        if (normalizer.notBlank(data.geo()))         contextLines.add("Geo:      " + data.geo());
        if (normalizer.notBlank(data.goal()))        contextLines.add("Goal:     " + data.goal());
        if (normalizer.notBlank(data.primaryKpis())) contextLines.add("KPIs:     " + data.primaryKpis());
        if (normalizer.notBlank(data.audienceAge())) contextLines.add("Audience age: " + data.audienceAge());

        StringBuilder contextBlock = new StringBuilder();
        if (!brf.isEmpty())            contextBlock.append("=== CAMPAIGN BRIEF ===\n").append(brf).append("\n\n");
        if (!contextLines.isEmpty())   contextBlock.append("=== CAMPAIGN CONTEXT ===\n")
                                                   .append(String.join("\n", contextLines)).append("\n\n");

        List<String> tacticLines = new ArrayList<>();
        for (Map.Entry<Integer, CampaignData.Tactic> e : data.tactics().entrySet()) {
            CampaignData.Tactic t = e.getValue();
            StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + ": " + t.name());
            if (t.imps() > 0) line.append(" (").append(fmt.intGroup(t.imps())).append(" imps)");
            tacticLines.add(line.toString());
        }

        List<String> keys = new ArrayList<>();
        for (Integer k : data.tactics().keySet()) keys.add("\"" + k + "\"");
        String tacticKeys = "[" + String.join(",", keys) + "]";

        String prompt =
            contextBlock
          + "You are a digital media analyst. For each tactic below, estimate:\n"
          + "1. Gender split of the reached audience.\n"
          + "2. Peak impression time window on WEEKDAYS (format: \"H AM/PM – H AM/PM\", e.g. \"7 PM – 11 PM\").\n"
          + "3. Peak impression time window on WEEKENDS (same format).\n\n"
          + "Tactics:\n" + String.join("\n", tacticLines) + "\n\n"
          + "Rules:\n"
          + "1. Return ONLY a valid JSON object — no markdown, no backticks.\n"
          + "2. Keys are tactic numbers as strings: " + tacticKeys + "\n"
          + "3. Each value: {\"male\": int, \"female\": int, \"weekdays_peak\": \"H AM/PM – H AM/PM\", \"weekends_peak\": \"H AM/PM – H AM/PM\"}\n"
          + "4. male + female = 100. All integers.\n"
          + "5. Gender: use campaign context as primary signal. Avoid defaulting to 50/50.\n"
          + "6. CRITICAL: Never use multiples of 5 for gender. Use uneven integers like 43,57,61,38.\n"
          + "7. Peak windows: whole hours, 2–5 hour range. Format: \"H PM – H PM\" (no leading zeros).\n\n"
          + "Example: {\"1\": {\"male\": 38, \"female\": 62, \"weekdays_peak\": \"7 PM – 11 PM\", \"weekends_peak\": \"10 AM – 2 PM\"}}";

        JsonNode parsed = call(prompt, 500, 30, "BatchB", false);
        if (parsed == null) return claudeDefaults.emptyTactical();

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
            if (!data.tactics().containsKey(n) || vals == null || !vals.isObject()) continue;
            int male = Math.max(0, Math.min(100, vals.path("male").asInt(50)));
            int female = 100 - male;
            String weekdays = normalizer.textOrNull(vals.get("weekdays_peak"));
            String weekends = normalizer.textOrNull(vals.get("weekends_peak"));
            if (weekdays != null) weekdays = weekdays.trim();
            if (weekends != null) weekends = weekends.trim();
            result.put(n, new ClaudeTactical.TacticInsight(male, female, weekdays, weekends));
        }
        return new ClaudeTactical(result);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BATCH C — results
    // ════════════════════════════════════════════════════════════════════════
    @Override
    public ClaudeResults batchResults(CampaignData data, String brief) {
        String brf = brief == null ? "" : brief;

        List<String> planLines = new ArrayList<>();
        if (normalizer.notBlank(data.client()))      planLines.add("Client:   " + data.client());
        if (normalizer.notBlank(data.campaign()))    planLines.add("Campaign: " + data.campaign());
        if (normalizer.notBlank(data.flightDates())) planLines.add("Flight:   " + data.flightDates());
        if (normalizer.notBlank(data.goal()))        planLines.add("Goal:     " + data.goal());
        if (normalizer.notBlank(data.budget()))      planLines.add("Budget:   " + data.budget());

        CampaignData.Totals tot = data.totals();
        List<String> totalLines = new ArrayList<>();
        if (tot != null) {
            if (tot.spend() > 0)   totalLines.add("Total Spend: $" + fmt.intGroup(Math.round(tot.spend())));
            if (tot.imps() > 0)    totalLines.add("Total Imps:  " + fmt.intGroup(tot.imps()));
            if (tot.ctr() != null) totalLines.add("Total CTR:   " + fmt.dec2(tot.ctr()) + "%");
            if (tot.vcr() != null) totalLines.add("Total VCR:   " + fmt.dec2(tot.vcr()) + "%");
        }

        List<String> tacticLines = new ArrayList<>();
        for (Map.Entry<Integer, CampaignData.Tactic> e : data.tactics().entrySet()) {
            CampaignData.Tactic t = e.getValue();
            StringBuilder line = new StringBuilder("  Tactic " + e.getKey() + " — " + t.name() + ":");
            if (t.spend() > 0)       line.append(" Actual Spend $").append(fmt.intGroup(Math.round(t.spend())));
            if (t.imps() > 0)        line.append(" | Actual Imps ").append(fmt.intGroup(t.imps()));
            if (t.ctr() != null)     line.append(" | Actual CTR ").append(fmt.dec2(t.ctr())).append('%');
            if (t.vcr() != null)     line.append(" | Actual VCR ").append(fmt.dec2(t.vcr())).append('%');
            if (t.planSpend() != null) line.append(" | Plan Spend $").append(fmt.intGroup(Math.round(t.planSpend())));
            if (t.planImps() != null)  line.append(" | Plan Imps ").append(fmt.intGroup(t.planImps()));
            if (t.planCtr() != null)   line.append(" | Plan CTR ").append(fmt.dec2(t.planCtr())).append('%');
            if (t.planVcr() != null)   line.append(" | Plan VCR ").append(fmt.dec2(t.planVcr())).append('%');
            tacticLines.add(line.toString());
        }

        List<String> ctx = new ArrayList<>();
        if (!brf.isEmpty())          ctx.add("=== CAMPAIGN BRIEF ===\n" + brf);
        if (!planLines.isEmpty())    ctx.add("=== CAMPAIGN PLAN ===\n" + String.join("\n", planLines));
        if (!totalLines.isEmpty())   ctx.add("=== OVERALL RESULTS ===\n" + String.join("\n", totalLines));
        if (!tacticLines.isEmpty())  ctx.add("=== RESULTS BY TACTIC ===\n" + String.join("\n", tacticLines));
        if (ctx.isEmpty()) return claudeDefaults.emptyResults();
        String context = String.join("\n\n", ctx);

        List<String> nums = new ArrayList<>();
        for (Integer k : data.tactics().keySet()) nums.add(String.valueOf(k));
        String tacticNums = String.join(", ", nums);

        String prompt =
            "You are a senior digital media analyst writing a post-campaign report for a client presentation.\n\n"
          + "ANALYTICAL PRINCIPLES — non-negotiable, apply to every text field:\n"
          + "1. OBSERVATION → EXPLANATION → RECOMMENDATION in every insight. "
          +    "State what happened, explain WHY it happened (name a specific cause: learning phase, creative fatigue, "
          +    "inventory constraints, bid competitiveness, audience saturation, pacing decision, seasonal effect, etc.), "
          +    "then state what this means or what should follow. All three parts are mandatory.\n"
          + "2. INTERPRET, NEVER ENUMERATE. The reader can see the numbers. Your job is to explain what they mean. "
          +    "WRONG: \"CTV delivered 12M impressions, the highest of any channel.\" "
          +    "RIGHT: \"CTV absorbed the majority of delivery because it was the only channel with priced inventory "
          +    "in the target geo during the campaign window — not a sign that other tactics underperformed, "
          +    "but a feature of the buy structure.\" Transform every data point into a business implication.\n"
          + "3. 'SO WHAT' IS MANDATORY. Every metric cited must be followed by its business consequence: "
          +    "did we hit goals? what does over/underdelivery mean for the brand? what did the audience actually receive?\n"
          + "4. NO GENERIC LANGUAGE. Forbidden: \"performance is tracking well\", \"results are in line with expectations\", "
          +    "\"we recommend monitoring\", \"this tactic requires optimization\". "
          +    "Every sentence must be specific to THIS campaign's numbers, channels, and audience.\n"
          + "5. NAME THE CAUSE. Don't say performance was strong — say why: audience targeting precision, "
          +    "creative format fit, placement quality, flight timing, competitive dynamics, etc.\n\n"
          + "Read the campaign data and return a JSON object with EXACTLY these keys:\n\n"
          + "{\n"
          + "  \"results_overview\": string,        // EXACTLY 2 SENTENCES. Past tense, no bullets, no line breaks. Hard limit: ≤380 chars total.\n"
          +    "  //  SENTENCE 1 — Overall result + key metric vs plan + reason WHY performance was as it was.\n"
          +    "  //    Must include: the most significant delivery outcome (over/underdelivery vs plan) + one specific cause\n"
          +    "  //    (budget pacing, inventory constraints, audience fit, bid dynamics, flight timing, etc.).\n"
          +    "  //    WRONG: \"The campaign delivered X impressions across Y tactics.\"\n"
          +    "  //    RIGHT: \"The campaign significantly underdelivered against planned reach goals due to budget pacing delays,\n"
          +    "  //            achieving only 4.8M impressions versus a planned 13M+ through Q1 spending constraints.\"\n"
          +    "  //  SENTENCE 2 — Tactic-level breakdown: which tactic(s) led performance and which lagged, with a specific reason for each.\n"
          +    "  //    Name the actual tactics. Include one metric per tactic (VCR%, CTR%, imps, spend). Name the cause.\n"
          +    "  //    RIGHT: \"Video tactics dominated actual delivery with strong completion rates (97% for Live Sports, 98% for CTV/Netflix),\n"
          +    "  //            while display formats like DOOH underperformed due to limited spend activation.\"\n"
          +    "  //  DO NOT write a third sentence. Stop after the second.\n"
          +    "  //  CLIENT-FACING TONE: This text goes directly into a client presentation.\n"
          +    "  //    Always lead with what went well or what was achieved — even in underdelivery scenarios, frame it as\n"
          +    "  //    a strategic constraint, not a failure. Highlight strong metrics (VCR, CTR, completion rates)\n"
          +    "  //    before mentioning gaps. If a tactic underperformed, attribute it to external factors\n"
          +    "  //    (inventory availability, budget pacing, market conditions) — never to poor execution.\n"
          + "  \"thoughts_on_performance\": string,  // EXACTLY 4 SHORT ANALYTICAL PARAGRAPHS separated by the literal string \" | \".\n"
          +    "  //  Each paragraph: 1–2 sentences, past tense, client-friendly. NOT bullet headers — flowing sentences.\n"
          +    "  //  REQUIRED STRUCTURE — exactly these 4 paragraphs in this order:\n"
          +    "  //  (1) Which tactic/channel performed best and the specific reason WHY (not just 'it performed well').\n"
          +    "  //  (2) Why the campaign succeeded overall — name the mechanism: targeting precision, audience-channel fit, creative alignment, etc.\n"
          +    "  //  (3) One creative or format insight — what worked and why (format size, video length, placement position, etc.).\n"
          +    "  //  (4) Efficiency or reach insight — what the spend delivered beyond raw impressions (CPM efficiency, frequency management, reach quality).\n"
          +    "  //  CRITICAL: produce EXACTLY 4 paragraphs — no more, no fewer. Result must contain EXACTLY 3 \" | \" separators.\n"
          +    "  //  BAD example: \"Programmatic video performed well.\" | \"Audience targeting was effective.\" | ...\n"
          +    "  //  GOOD example: \"Programmatic video exceeded impression goals by 0.6%, driven by strong inventory availability "
          +    "in the 25-44 demo during evening dayparts — the format's native environment for this audience.\" | ...\n"
          +    "  //  Total string including \" | \" separators must be ≤700 chars.\n"
          + "  \"tactic_overviews\": {               // Per-tactic. Keys: tactic numbers as strings (" + tacticNums + ")\n"
          + "    \"N\": string                        // MAX 190 CHARACTERS. End on a complete word and sentence.\n"
          +    "  //  STRUCTURE: [What the tactic delivered vs plan] + [WHY it performed as it did] + [business So what].\n"
          +    "  //  All three parts required even in 190 chars — be concise but complete.\n"
          +    "  //  WRONG: \"CTV delivered 5M impressions at 98% VCR, exceeding plan.\"\n"
          +    "  //  RIGHT: \"CTV delivered 5M impressions at 98% VCR (+2pp vs plan), driven by premium inventory selection — "
          +    "confirming the audience's high receptivity to full-screen video in this vertical.\"\n"
          +    "  //  Focus metrics by tactic type: Display→Imps+CTR; Video/Pre-roll→Imps+CTR+VCR; CTV/OTT→Imps+VCR; Audio→Completions.\n"
          +    "  //  Past tense. No bullets. Business English. Max 2 sentences.\n"
          + "  }\n"
          + "}\n\n"
          + "Rules:\n"
          + "- Return ONLY the JSON object — no markdown, no backticks, no explanation.\n"
          + "- null for results_overview / thoughts_on_performance if genuinely insufficient data.\n"
          + "- For tactic_overviews: include a key for every tactic number listed above.\n"
          + "- Do NOT invent metrics. Use only the numbers provided.\n"
          + "- CRITICAL: each tactic_overview value MUST end on a complete word/sentence and be ≤190 characters.\n"
          + "- thoughts_on_performance uses \" | \" (space-pipe-space) as paragraph separator — NOT newlines.\n"
          + "- DEPTH OVER BREADTH: one insight with a real explanation beats three that only restate numbers.\n"
          + "- Output in English.\n\n"
          + "Campaign data:\n" + context;

        JsonNode parsed = call(prompt, 3500, 60, "BatchC", true);
        if (parsed == null) return claudeDefaults.emptyResults();

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
                if (n <= 0) continue;
                tacticOverviews.put(n, normalizer.limitTacticOverview(ent.getValue().asText("")));
            }
        }
        return new ClaudeResults(resultsOverview, thoughts, tacticOverviews);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GEO summary — port of resolveGeoFromTab (max_tokens 60, ≤40 chars)
    // ════════════════════════════════════════════════════════════════════════
    @Override
    public String summarizeGeo(List<List<String>> geoRows) {
        if (geoRows == null || geoRows.isEmpty()) return null;
        StringBuilder tab = new StringBuilder();
        for (List<String> row : geoRows) {
            if (row == null) continue;
            tab.append(String.join(" | ", row)).append('\n');
        }
        String prompt =
            "Below is a 'Geo' tab from a media plan listing geographic targeting locations.\n"
          + "Summarise the locations into a single short comma-separated string (≤40 characters), "
          + "naming the most important regions/cities/states. No explanation — return only the string.\n\n"
          + tab;

        JsonNode resp = callRaw(prompt, 60, 30, "Geo");
        if (resp == null) return null;
        return normalizer.limitGeoSummary(normalizer.extractText(resp));
    }

    // ── HTTP plumbing ─────────────────────────────────────────────────────────

    /** Sends a prompt; returns the parsed JSON object from the model's text, or null. */
    JsonNode call(String prompt, int maxTokens, int timeoutSec, String label, boolean allowPartial) {

        JsonNode resp = callRaw(prompt, maxTokens, timeoutSec, label);
        if (resp == null) return null;
        if ("max_tokens".equals(resp.path("stop_reason").asText("")) && !allowPartial) {
            log.warn("[claude:{}] truncated by max_tokens", label);
            return null;
        }
        String text = normalizer.extractText(resp);
        if (text == null || text.isBlank()) return null;
        text = FENCE_OPEN.matcher(text.trim()).replaceFirst("");
        text = FENCE_CLOSE.matcher(text).replaceFirst("").trim();
        try {
            JsonNode node = json.readTree(text);
            if (node != null && node.isObject()) return node;
        } catch (Exception ignored) {
            // fall through to repair attempt
        }
        if (allowPartial) {
            try {
                String fixed = text.replaceAll("[,\\s]+$", "") + "}}";
                JsonNode node = json.readTree(fixed);
                if (node != null && node.isObject()) return node;
            } catch (Exception ignored) {
                // give up
            }
        }
        log.warn("[claude:{}] JSON parse failed", label);
        return null;
    }

    JsonNode callRaw(String prompt, int maxTokens, int timeoutSec, String label) {

        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("x-api-key", apiKey)
                .header("anthropic-version", VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.error("[claude:{}] error {} body={}", label, res.statusCode(), res.body());
                return null;
            }
            return json.readTree(res.body());
        } catch (Exception ex) {
            log.error("[claude:{}] request failed: {}", label, ex.getMessage());
            return null;
        }
    }
}
