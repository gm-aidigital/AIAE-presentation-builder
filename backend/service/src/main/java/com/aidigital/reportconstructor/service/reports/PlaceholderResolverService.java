package com.aidigital.reportconstructor.service.reports;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.engine.CampaignDataCollector;
import com.aidigital.reportconstructor.service.reports.engine.CampaignResolvers;
import com.aidigital.reportconstructor.service.reports.engine.Resolved;
import com.aidigital.reportconstructor.service.reports.engine.SheetUtils;
import com.aidigital.reportconstructor.service.reports.engine.TacticResolvers;
import com.aidigital.reportconstructor.service.reports.engine.TacticUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of {@code api/placeholders/builder.php} — {@code buildSections()} +
 * {@code buildFlatReplacements()}. This is the final orchestrator that calls
 * every campaign/tactic resolver and merges Claude batch output into the full
 * double-brace placeholder map used to fill the Google Slides deck.
 *
 * <p>Resolution priority (per resolver): manual Adjustments (adj) → Media Plan
 * (sheet) → Claude/computed → not_found. Unresolved tokens render as
 * {@code "—"} in {@link #buildFlatReplacements}.
 */
@Service
public class PlaceholderResolverService {

    private static final String DASH = "\u2014"; // —

    public record Labels(List<LabelChip> sheet, List<LabelChip> adj) {}
    public record LabelChip(String label, String value) {}

    /**
     * Preview output for the constructor UI.
     *
     * @param sections   resolved placeholder sections
     * @param labels     "all labels" chips for the sheet/adj panels
     * @param found      number of resolved placeholders
     * @param total      total number of placeholders
     * @param sheetCount number of Media-Plan (sheet) rows in the request
     * @param adjCount   number of Adjustments rows in the request
     */
    public record PreviewResult(
        List<PreviewSection> sections, Labels labels, int found, int total,
        int sheetCount, int adjCount
    ) {}

    /** Preview path — no Claude calls (PHP preview builds sections with empty cc). */
    public PreviewResult resolve(GeneratePayload payload) {
        CampaignData data = collectData(payload);
        List<PreviewSection> sections = buildSections(
            payload, data,
            ClaudeStrategic.empty(), ClaudeTactical.empty(), ClaudeResults.empty(), null
        );

        int total = 0, found = 0;
        for (PreviewSection sec : sections) {
            for (Placeholder ph : sec.placeholders()) {
                total++;
                if (!"not_found".equals(ph.source())) found++;
            }
        }
        int sheetCount = payload.sheetRows() == null ? 0 : payload.sheetRows().size();
        int adjCount = payload.adjRows() == null ? 0 : payload.adjRows().size();
        return new PreviewResult(sections, collectAllLabels(payload), found, total, sheetCount, adjCount);
    }

    /** Generate path — flat {@code {{token}} → value} map; unresolved => "—". */
    public Map<String, String> buildFlatReplacements(
        GeneratePayload payload, CampaignData data,
        ClaudeStrategic ccA, ClaudeTactical ccB, ClaudeResults ccC, String geoSummary
    ) {
        List<PreviewSection> sections = buildSections(payload, data, ccA, ccB, ccC, geoSummary);
        Map<String, String> flat = new LinkedHashMap<>();
        for (PreviewSection sec : sections) {
            for (Placeholder ph : sec.placeholders()) {
                String v = ph.value();
                flat.put(ph.key(), v == null || v.isEmpty() ? DASH : v);
            }
        }
        return flat;
    }

    /** Single-pass campaign aggregation — shared between preview and generate. */
    public CampaignData collectData(GeneratePayload payload) {
        return CampaignDataCollector.collect(
            payload.sheetRows(), payload.adjRows(), payload.audienceRows(),
            payload.estimatesRows(), payload.lineItemMapping()
        );
    }

    // ── Claude batch gating (port of needA/needB/needC in builder.php) ────────

    public boolean needStrategic(GeneratePayload payload) {
        List<List<String>> adj = payload.adjRows();
        List<List<String>> sheet = payload.sheetRows();
        if (bothNull(adj, sheet, "Audience age:")) return true;
        if (bothNull(adj, sheet, "Audience segments:")) return true;
        if (bothNull(adj, sheet, "Proposal overview:")) return true;
        for (int i = 1; i <= 4; i++) {
            if (bothNull(adj, sheet, "Strategic point " + i + ":")) return true;
            if (bothNull(adj, sheet, "Strategic overview " + i + ":")) return true;
        }
        return false;
    }

    public boolean needTactical(GeneratePayload payload, CampaignData data) {
        if (data == null || data.tactics() == null) return false;
        List<List<String>> adj = payload.adjRows();
        List<List<String>> sheet = payload.sheetRows();
        for (int n : data.tactics().keySet()) {
            if (bothNull(adj, sheet, "Tactic " + n + " male:")
                || bothNull(adj, sheet, "Tactic " + n + " weekdays:")) {
                return true;
            }
        }
        return false;
    }

    public boolean needResults(GeneratePayload payload, CampaignData data) {
        List<List<String>> adj = payload.adjRows();
        List<List<String>> sheet = payload.sheetRows();
        if (bothNull(adj, sheet, "Our results overview:")) return true;
        if (bothNull(adj, sheet, "Thoughts on the performance:")) return true;
        if (data != null && data.tactics() != null) {
            for (int n : data.tactics().keySet()) {
                if (bothNull(adj, sheet, "Tactic " + n + " overview:")) return true;
            }
        }
        return false;
    }

    /** True only when the "Geo" cell points at the Geo tab and no manual value exists. */
    public boolean needGeoSummary(GeneratePayload payload) {
        List<List<String>> adj = payload.adjRows();
        List<List<String>> sheet = payload.sheetRows();
        if (SheetUtils.findLabelValue(adj, "Geo locations:") != null) return false;
        if (SheetUtils.findLabelValue(sheet, "Geo locations:") != null) return false;
        String below = SheetUtils.findLabelValueBelow(sheet, "Geo");
        if (below == null) return false;
        String lc = below.toLowerCase(java.util.Locale.ROOT);
        return lc.contains("see geo tab") || lc.contains("geo tab");
    }

    // ── Section assembly (port of buildSections) ──────────────────────────────

    private List<PreviewSection> buildSections(
        GeneratePayload payload, CampaignData data,
        ClaudeStrategic ccA, ClaudeTactical ccB, ClaudeResults ccC, String geoSummary
    ) {
        List<List<String>> sheet = payload.sheetRows();
        List<List<String>> adj = payload.adjRows();
        String reportType = payload.reportType();
        List<String> mediaTactics = TacticUtils.extractTacticsFromMedia(sheet);

        List<PreviewSection> sections = new ArrayList<>();

        // ── 1. Начало ─────────────────────────────────────────────────────────
        Map<String, Resolved> start = new LinkedHashMap<>();
        start.put("{{client_name}}", CampaignResolvers.resolve(sheet, adj, "Client name:"));
        start.put("{{Campaign_name}}", CampaignResolvers.resolve(sheet, adj, "Campaign:"));
        if (reportType != null && !reportType.isBlank()) {
            start.put("{{report_type}}", new Resolved("Report type (UI)", reportType, "sheet"));
        } else {
            start.put("{{report_type}}", CampaignResolvers.resolve(sheet, adj, "Report type:"));
        }
        start.put("{{flight_dates}}", CampaignResolvers.resolveFlightDates(sheet, adj));
        start.put("{{total_investment}}", CampaignResolvers.resolveTotalInvestment(sheet, adj, data));
        start.put("{{primary_kpis}}", CampaignResolvers.resolvePrimaryKpis(sheet, adj));
        start.put("{{audience_age}}", CampaignResolvers.resolveAudienceAge(sheet, adj, ccA.audienceAge()));
        start.put("{{audience_segments}}", CampaignResolvers.resolveAudienceSegments(sheet, adj, ccA.audienceSegments()));
        start.put("{{geo_locations}}", CampaignResolvers.resolveGeoLocations(sheet, adj, geoSummary));
        start.put("{{funnel_stages}}", CampaignResolvers.resolveFunnelStages(sheet, adj));
        start.put("{{tactics_list}}", CampaignResolvers.resolveTacticsList(sheet, adj));
        sections.add(section("Начало", start));

        // ── 2. Обзорные слайды ──────────────────────────────────────────────────
        Map<String, Resolved> overview = new LinkedHashMap<>();
        overview.put("{{proposal overview}}", CampaignResolvers.resolveProposalOverview(sheet, adj, ccA.proposalOverview()));
        overview.put("{{Our results overview}}", CampaignResolvers.resolveResultsOverview(sheet, adj, ccC.resultsOverview()));
        overview.putAll(CampaignResolvers.resolveThoughtsOnPerformance(sheet, adj, ccC.thoughtsOnPerformance()));
        sections.add(section("Обзорные слайды", overview));

        // ── 3. Стратегические инсайты ────────────────────────────────────────────
        sections.add(section("Стратегические инсайты",
            CampaignResolvers.resolveStrategicInsights(sheet, adj, ccA.strategicInsights())));

        // ── 4. Суммарные метрики ─────────────────────────────────────────────────
        Map<String, Resolved> totals = new LinkedHashMap<>();
        totals.put("{{total imps}}", CampaignResolvers.resolveTotalImps(sheet, adj, data));
        totals.put("{{total ctr}}", CampaignResolvers.resolveTotalCtr(sheet, adj, data));
        totals.put("{{total vcr}}", CampaignResolvers.resolveTotalVcr(sheet, adj, data));
        totals.put("{{total spend}}", CampaignResolvers.resolveTotalInvestment(sheet, adj, data));
        sections.add(section("Суммарные метрики", totals));

        // ── 5–10. Тактики 1–6 (полный набор) ─────────────────────────────────────
        for (int n = 1; n <= 6; n++) {
            sections.add(section("Тактика " + n,
                tacticFull(n, sheet, adj, data, ccB, ccC, mediaTactics)));
        }
        // ── 11. Тактика 7 (урезанный набор) ──────────────────────────────────────
        sections.add(section("Тактика 7",
            tacticShort(7, sheet, adj, data, ccB, ccC, mediaTactics)));

        return sections;
    }

    private static Map<String, Resolved> tacticFull(
        int n, List<List<String>> sheet, List<List<String>> adj, CampaignData data,
        ClaudeTactical ccB, ClaudeResults ccC, List<String> mediaTactics
    ) {
        Resolved info = resolveTacticName(n, sheet, adj, mediaTactics);
        String tacticName = info.value() == null ? "" : info.value();

        Map<String, Resolved> m = new LinkedHashMap<>();
        m.put("{{tactic " + n + "}}", info);
        m.put("{{tactic " + n + " goal}}", TacticResolvers.resolveTacticGoal(n, sheet, adj));
        m.put("{{tactic " + n + " overview}}", TacticResolvers.resolveTacticOverview(n, sheet, adj, ccC));
        m.put("{{tactic " + n + " spend}}", TacticResolvers.resolveTacticSpend(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " imps}}", TacticResolvers.resolveTacticImps(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " reach}}", TacticResolvers.resolveTacticReach(n, sheet, adj, data));
        m.put("{{tactic " + n + " ctr}}", TacticResolvers.resolveTacticCtr(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " vcr}}", TacticResolvers.resolveTacticVcr(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " volume}}", CampaignResolvers.resolve(sheet, adj, "Tactic " + n + " volume:"));
        m.put("{{tactic " + n + " \u2013 bench}}", TacticResolvers.resolveTacticBench(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " male}}", TacticResolvers.resolveTacticGender(n, "male", sheet, adj, ccB));
        m.put("{{tactic " + n + " female}}", TacticResolvers.resolveTacticGender(n, "female", sheet, adj, ccB));
        m.put("{{tactic " + n + " f}}", TacticResolvers.resolveTacticFreq(n, sheet, adj, data));
        m.put("{{tactic " + n + " weekdays}}", TacticResolvers.resolveTacticDaypart(n, "weekdays", sheet, adj, ccB));
        m.put("{{tactic " + n + " weekends}}", TacticResolvers.resolveTacticDaypart(n, "weekends", sheet, adj, ccB));
        m.put("{{tactic " + n + " top creative name}}", TacticResolvers.resolveTacticTopCreativeName(n, sheet, adj, data));
        m.put("{{tactic " + n + " top creative imps}}", TacticResolvers.resolveTacticTopCreativeImps(n, sheet, adj, data));
        m.put("{{tactic " + n + " top creative clicks}}", TacticResolvers.resolveTacticTopCreativeClicks(n, sheet, adj, data));
        return m;
    }

    private static Map<String, Resolved> tacticShort(
        int n, List<List<String>> sheet, List<List<String>> adj, CampaignData data,
        ClaudeTactical ccB, ClaudeResults ccC, List<String> mediaTactics
    ) {
        Resolved info = resolveTacticName(n, sheet, adj, mediaTactics);
        String tacticName = info.value() == null ? "" : info.value();

        Map<String, Resolved> m = new LinkedHashMap<>();
        m.put("{{tactic " + n + "}}", info);
        m.put("{{tactic " + n + " goal}}", TacticResolvers.resolveTacticGoal(n, sheet, adj));
        m.put("{{tactic " + n + " overview}}", TacticResolvers.resolveTacticOverview(n, sheet, adj, ccC));
        m.put("{{tactic " + n + " spend}}", TacticResolvers.resolveTacticSpend(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " imps}}", TacticResolvers.resolveTacticImps(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " reach}}", TacticResolvers.resolveTacticReach(n, sheet, adj, data));
        m.put("{{tactic " + n + " ctr}}", TacticResolvers.resolveTacticCtr(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " vcr}}", TacticResolvers.resolveTacticVcr(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " \u2013 bench}}", TacticResolvers.resolveTacticBench(n, tacticName, sheet, adj, data));
        m.put("{{tactic " + n + " male}}", TacticResolvers.resolveTacticGender(n, "male", sheet, adj, ccB));
        m.put("{{tactic " + n + " female}}", TacticResolvers.resolveTacticGender(n, "female", sheet, adj, ccB));
        m.put("{{tactic " + n + " f}}", TacticResolvers.resolveTacticFreq(n, sheet, adj, data));
        m.put("{{tactic " + n + " weekdays}}", TacticResolvers.resolveTacticDaypart(n, "weekdays", sheet, adj, ccB));
        m.put("{{tactic " + n + " weekends}}", TacticResolvers.resolveTacticDaypart(n, "weekends", sheet, adj, ccB));
        return m;
    }

    private static Resolved resolveTacticName(
        int n, List<List<String>> sheet, List<List<String>> adj, List<String> mediaTactics
    ) {
        Resolved manual = CampaignResolvers.resolve(sheet, adj, "Tactic " + n + ":");
        if (manual.found()) return manual;
        int idx = n - 1;
        if (mediaTactics != null && idx < mediaTactics.size()
            && mediaTactics.get(idx) != null && !mediaTactics.get(idx).isEmpty()) {
            return new Resolved("Tactic " + n + " (auto: Media column)",
                TacticUtils.normalizeTacticDisplayName(mediaTactics.get(idx)), "sheet");
        }
        return Resolved.notFound("Tactic " + n + ":");
    }

    private static PreviewSection section(String title, Map<String, Resolved> entries) {
        List<Placeholder> phs = new ArrayList<>();
        for (Map.Entry<String, Resolved> e : entries.entrySet()) {
            Resolved r = e.getValue();
            phs.add(new Placeholder(e.getKey(), r.label(), r.value(), r.source()));
        }
        return new PreviewSection(title, phs);
    }

    private static boolean bothNull(List<List<String>> adj, List<List<String>> sheet, String label) {
        return SheetUtils.findLabelValue(adj, label) == null
            && SheetUtils.findLabelValue(sheet, label) == null;
    }

    /** Port of collectAllLabels — chips for the preview "all labels" panel. */
    private static Labels collectAllLabels(GeneratePayload payload) {
        return new Labels(chips(payload.sheetRows()), chips(payload.adjRows()));
    }

    private static List<LabelChip> chips(List<List<String>> rows) {
        List<LabelChip> out = new ArrayList<>();
        if (rows == null) return out;
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) continue;
            String label = row.get(0) == null ? "" : row.get(0).trim();
            if (label.isEmpty()) continue;
            if (row.size() < 2 || row.get(1) == null) continue;
            String value = row.get(1).trim();
            if (value.isEmpty()) continue;
            out.add(new LabelChip(label, value));
        }
        return out;
    }
}
