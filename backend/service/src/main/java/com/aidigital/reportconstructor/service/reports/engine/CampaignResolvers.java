package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Java port of {@code api/placeholders/resolvers_campaign.php}.
 *
 * <p>Campaign-level placeholder resolvers. Each returns a {@link Resolved}
 * ({@code label}, {@code value}, {@code source}); {@code value == null} ⇒
 * unresolved. Priority is always manual Adjustments → Media Plan → computed /
 * Claude. Claude outputs are passed in (the resolvers never call the API).
 */
public final class CampaignResolvers {

    private CampaignResolvers() {}

    /** {@code resolve()} — generic label lookup, adj → sheet → not_found. */
    public static Resolved resolve(List<List<String>> sheetRows, List<List<String>> adjRows, String label) {
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        return Resolved.notFound(label);
    }

    public static Resolved resolveFlightDates(List<List<String>> sheetRows, List<List<String>> adjRows) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Flight dates:");
        if (fromAdj != null) return new Resolved("Flight dates:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Flight dates:");
        if (fromSheet != null) return new Resolved("Flight dates:", fromSheet, "sheet");

        String fromAdjAuto = SheetUtils.extractFlightDates(adjRows);
        if (fromAdjAuto != null) return new Resolved("Flight Start / Flight End columns", fromAdjAuto, "adj");
        String fromSheetAuto = SheetUtils.extractFlightDates(sheetRows);
        if (fromSheetAuto != null) return new Resolved("Flight Start / Flight End columns", fromSheetAuto, "sheet");
        return Resolved.notFound("Flight Start / Flight End columns");
    }

    public static Resolved resolvePrimaryKpis(List<List<String>> sheetRows, List<List<String>> adjRows) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Primary KPIs:");
        if (fromAdj != null) return new Resolved("Primary KPIs:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Primary KPIs:");
        if (fromSheet != null) return new Resolved("Primary KPIs:", fromSheet, "sheet");

        int headerRowIdx = -1, channelCol = -1;
        outer:
        for (int i = 0; i < adjRows.size(); i++) {
            List<String> row = adjRows.get(i);
            if (row == null) continue;
            for (int j = 0; j < row.size(); j++) {
                if (cell(row, j).toLowerCase(Locale.ROOT).equals("channel")) {
                    headerRowIdx = i; channelCol = j; break outer;
                }
            }
        }
        if (headerRowIdx < 0) return Resolved.notFound("Primary KPIs (auto: Channel)");

        Map<String, Boolean> channels = new LinkedHashMap<>();
        for (int i = headerRowIdx + 1; i < adjRows.size(); i++) {
            String val = cellAt(adjRows.get(i), channelCol).toLowerCase(Locale.ROOT);
            if (!val.isEmpty()) channels.put(val, true);
        }
        if (channels.isEmpty()) return Resolved.notFound("Primary KPIs (auto: Channel)");

        boolean hasDisplay = false, hasVideo = false;
        for (String ch : channels.keySet()) {
            if (ch.contains("display")) hasDisplay = true;
            if (ch.contains("video")) hasVideo = true;
        }
        String kpiValue;
        if (hasDisplay && hasVideo) kpiValue = "Multiple tactics";
        else if (hasDisplay) kpiValue = "Imps, CTR, R&F";
        else if (hasVideo) kpiValue = "Imps, VCR, R&F";
        else kpiValue = "Multiple tactics";
        return new Resolved("Primary KPIs (auto: Channel)", kpiValue, "adj");
    }

    public static Resolved resolveAudienceAge(List<List<String>> sheetRows, List<List<String>> adjRows, String claudeAge) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Audience age:");
        if (fromAdj != null) return new Resolved("Audience age:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Audience age:");
        if (fromSheet != null) return new Resolved("Audience age:", fromSheet, "sheet");
        if (claudeAge != null) return new Resolved("Audience age (auto: Claude from brief)", claudeAge, "adj");
        return Resolved.notFound("Audience age:");
    }

    public static Resolved resolveAudienceSegments(List<List<String>> sheetRows, List<List<String>> adjRows, String claudeSegs) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Audience segments:");
        if (fromAdj != null) return new Resolved("Audience segments:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Audience segments:");
        if (fromSheet != null) return new Resolved("Audience segments:", fromSheet, "sheet");
        if (claudeSegs != null) return new Resolved("Audience segments (auto: Claude from Audience&Inventory tab)", claudeSegs, "sheet");
        return Resolved.notFound("Audience segments:");
    }

    /**
     * {@code geoSummary} is the Claude-summarised Geo-tab string, pre-computed by
     * the orchestrator only when the "Geo" cell points at the Geo tab.
     */
    public static Resolved resolveGeoLocations(List<List<String>> sheetRows, List<List<String>> adjRows, String geoSummary) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Geo locations:");
        if (fromAdj != null) return new Resolved("Geo locations:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Geo locations:");
        if (fromSheet != null) return new Resolved("Geo locations:", fromSheet, "sheet");

        String below = SheetUtils.findLabelValueBelow(sheetRows, "Geo");
        if (below != null) {
            String lc = below.toLowerCase(Locale.ROOT);
            if (lc.contains("see geo tab") || lc.contains("geo tab")) {
                if (geoSummary != null && !geoSummary.isBlank()) {
                    return new Resolved("Geo (from Geo tab via Claude)", geoSummary, "claude");
                }
                return new Resolved("Geo (value below)", below, "sheet");
            }
            return new Resolved("Geo (value below)", below, "sheet");
        }
        return Resolved.notFound("Geo locations:");
    }

    public static Resolved resolveFunnelStages(List<List<String>> sheetRows, List<List<String>> adjRows) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Funnel stages:");
        if (fromAdj != null) return new Resolved("Funnel stages:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Funnel stages:");
        if (fromSheet != null) return new Resolved("Funnel stages:", fromSheet, "sheet");
        String below = SheetUtils.findLabelValueBelow(sheetRows, "Goal");
        if (below != null) return new Resolved("Goal (value below)", below, "sheet");
        return Resolved.notFound("Funnel stages:");
    }

    public static Resolved resolveTacticsList(List<List<String>> sheetRows, List<List<String>> adjRows) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Tactics list:");
        if (fromAdj != null) return new Resolved("Tactics list:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Tactics list:");
        if (fromSheet != null) return new Resolved("Tactics list:", fromSheet, "sheet");

        Map<String, String> known = TacticUtils.knownTacticsWhitelist();
        int mediaRowIdx = -1, mediaColIdx = -1;
        outer:
        for (int i = 0; i < sheetRows.size(); i++) {
            List<String> row = sheetRows.get(i);
            if (row == null) continue;
            for (int j = 0; j < row.size(); j++) {
                if (cell(row, j).toLowerCase(Locale.ROOT).equals("media")) {
                    mediaRowIdx = i; mediaColIdx = j; break outer;
                }
            }
        }
        if (mediaRowIdx < 0) return Resolved.notFound("Tactics list (auto: 20 rows below \"Media\")");

        Map<String, Boolean> seen = new LinkedHashMap<>();
        List<String> result = new ArrayList<>();
        int limit = Math.min(mediaRowIdx + 20, sheetRows.size() - 1);
        for (int i = mediaRowIdx + 1; i <= limit; i++) {
            String c = cellAt(sheetRows.get(i), mediaColIdx);
            if (c.isEmpty()) continue;
            String normalized = c.toLowerCase(Locale.ROOT);
            String canonical = known.get(normalized);
            if (canonical == null) continue;
            String canonicalKey = canonical.toLowerCase(Locale.ROOT);
            if (!seen.containsKey(canonicalKey)) {
                seen.put(canonicalKey, true);
                result.add(TacticUtils.normalizeTacticDisplayName(canonical));
            }
        }
        if (result.isEmpty()) return Resolved.notFound("Tactics list (auto: 20 rows below \"Media\")");
        return new Resolved("Tactics list (auto: 20 rows below \"Media\")", String.join(", ", result), "sheet");
    }

    public static Resolved resolveProposalOverview(List<List<String>> sheetRows, List<List<String>> adjRows, String claudeOverview) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Proposal overview:");
        if (fromAdj != null) return new Resolved("Proposal overview:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Proposal overview:");
        if (fromSheet != null) return new Resolved("Proposal overview:", fromSheet, "sheet");
        if (claudeOverview != null) return new Resolved("Proposal overview (auto: Claude from brief + media plan)", claudeOverview, "adj");
        return Resolved.notFound("Proposal overview:");
    }

    /** Returns the 8 {@code {{Strategic point/overview N}}} entries. */
    public static Map<String, Resolved> resolveStrategicInsights(
        List<List<String>> sheetRows, List<List<String>> adjRows, List<ClaudeStrategic.StrategicInsight> claude) {

        Map<String, Resolved> result = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) {
            String mPoint = coalesce(SheetUtils.findLabelValue(adjRows, "Strategic point " + i + ":"),
                                     SheetUtils.findLabelValue(sheetRows, "Strategic point " + i + ":"));
            String mOver = coalesce(SheetUtils.findLabelValue(adjRows, "Strategic overview " + i + ":"),
                                    SheetUtils.findLabelValue(sheetRows, "Strategic overview " + i + ":"));
            ClaudeStrategic.StrategicInsight ci = claude != null && claude.size() >= i ? claude.get(i - 1) : null;

            String pointKey = "{{Strategic point " + i + "}}";
            if (mPoint != null) {
                result.put(pointKey, new Resolved("Strategic point " + i + ":", mPoint, "adj"));
            } else if (ci != null && notBlank(ci.point())) {
                result.put(pointKey, new Resolved("Strategic point " + i + " (auto: Claude)", ci.point(), "adj"));
            } else {
                result.put(pointKey, Resolved.notFound("Strategic point " + i + ":"));
            }

            String overKey = "{{Strategic overview " + i + "}}";
            if (mOver != null) {
                result.put(overKey, new Resolved("Strategic overview " + i + ":", mOver, "adj"));
            } else if (ci != null && notBlank(ci.overview())) {
                result.put(overKey, new Resolved("Strategic overview " + i + " (auto: Claude)", ci.overview(), "adj"));
            } else {
                result.put(overKey, Resolved.notFound("Strategic overview " + i + ":"));
            }
        }
        return result;
    }

    public static Resolved resolveResultsOverview(List<List<String>> sheetRows, List<List<String>> adjRows, String claudeOverview) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Our results overview:");
        if (fromAdj != null) return new Resolved("Our results overview:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Our results overview:");
        if (fromSheet != null) return new Resolved("Our results overview:", fromSheet, "sheet");
        if (claudeOverview != null) return new Resolved("Our results overview (auto: Claude)", claudeOverview, "adj");
        return Resolved.notFound("Our results overview:");
    }

    /** Returns the 4 {@code {{thoughts on the performance N}}} entries. */
    public static Map<String, Resolved> resolveThoughtsOnPerformance(
        List<List<String>> sheetRows, List<List<String>> adjRows, List<String> claudeThoughts) {

        String[] parts;
        String source;
        String label;
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Thoughts on the performance:");
        if (fromAdj != null) {
            parts = split4(fromAdj); source = "adj"; label = "Thoughts on the performance:";
        } else {
            String fromSheet = SheetUtils.findLabelValue(sheetRows, "Thoughts on the performance:");
            if (fromSheet != null) {
                parts = split4(fromSheet); source = "sheet"; label = "Thoughts on the performance:";
            } else if (claudeThoughts != null && !claudeThoughts.isEmpty()) {
                parts = new String[4];
                for (int i = 0; i < 4; i++) parts[i] = i < claudeThoughts.size() ? claudeThoughts.get(i) : null;
                source = "claude"; label = "Thoughts on the performance (auto: Claude)";
            } else {
                parts = new String[4]; source = "not_found"; label = "Thoughts on the performance:";
            }
        }
        Map<String, Resolved> result = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) {
            result.put("{{thoughts on the performance " + i + "}}",
                new Resolved(label + " [" + i + "]", parts[i - 1], source));
        }
        return result;
    }

    public static Resolved resolveTotalImps(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Total imps:");
        if (fromAdj != null) return new Resolved("Total imps:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Total imps:");
        if (fromSheet != null) return new Resolved("Total imps:", fromSheet, "sheet");
        double imps = data.totals().imps();
        if (imps > 0) return new Resolved("Total imps (auto: BQ Impressions)", Fmt.intGroup(imps), "adj");
        return Resolved.notFound("Total imps (auto: BQ Impressions)");
    }

    public static Resolved resolveTotalInvestment(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Total investment:");
        if (fromAdj != null) return new Resolved("Total investment:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Total investment:");
        if (fromSheet != null) return new Resolved("Total investment:", fromSheet, "sheet");
        double spend = data.totals().spend();
        if (spend > 0) return new Resolved("Total investment (auto: BQ spend)", Fmt.money(spend), "adj");
        return Resolved.notFound("Total investment (auto: BQ spend)");
    }

    public static Resolved resolveTotalCtr(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Total CTR:");
        if (fromAdj != null) return new Resolved("Total CTR:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Total CTR:");
        if (fromSheet != null) return new Resolved("Total CTR:", fromSheet, "sheet");
        Double ctr = data.totals().ctr();
        if (ctr != null) return new Resolved("Total CTR (auto: Clicks / Imps)", Fmt.pctOrDash(ctr), "adj");
        return Resolved.notFound("Total CTR (auto: Clicks / Imps)");
    }

    public static Resolved resolveTotalVcr(List<List<String>> sheetRows, List<List<String>> adjRows, CampaignData data) {
        String fromAdj = SheetUtils.findLabelValue(adjRows, "Total VCR:");
        if (fromAdj != null) return new Resolved("Total VCR:", fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, "Total VCR:");
        if (fromSheet != null) return new Resolved("Total VCR:", fromSheet, "sheet");
        Double vcr = data.totals().vcr();
        if (vcr != null) return new Resolved("Total VCR (auto: Completions / Imps)", Fmt.pctOrDash(vcr), "adj");
        return Resolved.notFound("Total VCR (auto: Completions / Imps)");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String[] split4(String raw) {
        String[] out = new String[4];
        if (raw == null || raw.trim().isEmpty()) return out;
        String[] parts = raw.split(" \\| ");
        for (int i = 0; i < 4; i++) {
            String p = i < parts.length ? parts[i].trim() : null;
            out[i] = (p == null || p.isEmpty()) ? null : p;
        }
        return out;
    }

    private static String coalesce(String a, String b) {
        return a != null ? a : b;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String cell(List<String> row, int idx) {
        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    private static String cellAt(List<String> row, int idx) {
        if (row == null || idx < 0 || idx >= row.size()) return "";
        return cell(row, idx);
    }
}
