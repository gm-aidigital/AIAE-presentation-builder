package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Java port of {@code api/placeholders/resolvers_tactics.php} — the per-tactic
 * metric resolvers. Each method mirrors the PHP {@code resolveTacticX} priority:
 * manual Adjustments (adj) → Media Plan (sheet) → computed/Claude → not_found.
 */
public final class TacticResolvers {

    private TacticResolvers() {}

    private static final String DASH = "\u2014"; // —

    public static Resolved resolveTacticSpend(int n, String tacticName, List<List<String>> sheetRows,
                                              List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " spend:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        if (t != null && t.spend() > 0) {
            return new Resolved(label + " (auto: BQ Cost)", "$" + Fmt.intGroup(t.spend()), "adj");
        }
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticImps(int n, String tacticName, List<List<String>> sheetRows,
                                             List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " imps:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        if (t != null && t.imps() > 0) {
            return new Resolved(label + " (auto: BQ Impressions)", Fmt.intGroup(t.imps()), "adj");
        }
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticBench(int n, String tacticName, List<List<String>> sheetRows,
                                              List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " benchmark:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");

        String kpiType = TacticUtils.getTacticKpiType(tacticName);
        CampaignData.Tactic t = tactic(data, n);
        if ("ctr".equals(kpiType)) {
            Double val = t == null ? null : t.planCtr();
            if (val != null) {
                return new Resolved(label + " (auto: Estimates CTR)", "CTR \u2013 " + Fmt.dec2(val) + "%", "adj");
            }
            return Resolved.notFound(label);
        }
        if ("vcr".equals(kpiType)) {
            Double val = t == null ? null : t.planVcr();
            if (val != null) {
                return new Resolved(label + " (auto: Estimates VCR)", "VCR \u2013 " + Math.round(val) + "%", "adj");
            }
            return Resolved.notFound(label);
        }
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticCtr(int n, String tacticName, List<List<String>> sheetRows,
                                            List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " CTR:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        Double ctr = t == null ? null : t.ctr();
        if (ctr != null) {
            return new Resolved(label + " (auto: Clicks/Imps)", Fmt.pctOrDash(ctr), "adj");
        }
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticVcr(int n, String tacticName, List<List<String>> sheetRows,
                                            List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " VCR:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        Double vcr = t == null ? null : t.vcr();
        if (vcr != null) {
            return new Resolved(label + " (auto: Completions/Imps)", Fmt.pctOrDash(vcr), "adj");
        }
        if (t != null) {
            return new Resolved(label + " (auto: no completions)", DASH, "adj");
        }
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticFreq(int n, List<List<String>> sheetRows,
                                             List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " f:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        Double maxFreq = t == null ? null : t.planMaxFreq();
        if (maxFreq != null && maxFreq > 0) {
            double freq = TacticUtils.freqFromMax(n, maxFreq);
            int pct = (int) Math.round((1 - freq / maxFreq) * 100);
            return new Resolved(label + " (auto: Estimates max freq \u2212 " + pct + "%)", Fmt.dec2(freq), "adj");
        }
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticReach(int n, List<List<String>> sheetRows,
                                              List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " reach:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        Double maxFreq = t == null ? null : t.planMaxFreq();
        if (maxFreq == null || maxFreq <= 0) return Resolved.notFound(label);
        double freq = TacticUtils.freqFromMax(n, maxFreq);
        if (freq <= 0) return Resolved.notFound(label);
        double imps = t.imps();
        if (imps <= 0) imps = t.planImps() == null ? 0.0 : t.planImps();
        if (imps <= 0) return Resolved.notFound(label);
        long reach = Math.round(imps / freq);
        return new Resolved(label + " (auto: imps / freq)", Fmt.intGroup(reach), "adj");
    }

    public static Resolved resolveTacticGoal(int n, List<List<String>> sheetRows, List<List<String>> adjRows) {
        String label = "Tactic " + n + " goal:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");

        int mediaRowIdx = -1, mediaColIdx = -1, goalColIdx = -1;
        for (int i = 0; i < sheetRows.size(); i++) {
            List<String> row = sheetRows.get(i);
            if (row == null) continue;
            boolean hasMedia = false;
            int mCol = -1, gCol = -1;
            for (int j = 0; j < row.size(); j++) {
                String v = cell(row, j).toLowerCase(Locale.ROOT);
                if (v.equals("media")) { hasMedia = true; mCol = j; }
                if (v.equals("goal")) { gCol = j; }
            }
            if (hasMedia && mCol >= 0) {
                mediaRowIdx = i; mediaColIdx = mCol; goalColIdx = gCol; break;
            }
        }
        if (mediaRowIdx < 0 || goalColIdx < 0) return Resolved.notFound(label);

        String[] stopWords = {"added value", "totals", "please note", "total:"};
        List<List<String>> tacticRows = new ArrayList<>();
        for (int i = mediaRowIdx + 1; i < sheetRows.size(); i++) {
            List<String> row = sheetRows.get(i);
            String c = cellAt(row, mediaColIdx);
            String rowText = joinLower(row, 4);
            boolean stop = false;
            for (String sw : stopWords) {
                if (rowText.contains(sw)) { stop = true; break; }
            }
            if (stop) break;
            if (c.isEmpty()) break;
            tacticRows.add(row);
        }

        if (n - 1 >= tacticRows.size()) return Resolved.notFound(label);
        List<String> tacticRow = tacticRows.get(n - 1);
        String rawGoal = cellAt(tacticRow, goalColIdx);
        if (rawGoal.isEmpty()) return Resolved.notFound(label);

        String key = rawGoal.toLowerCase(Locale.ROOT);
        String mapped = switch (key) {
            case "awareness" -> "AWARENESS";
            case "consideration & engagement" -> "CONSIDERATION";
            case "conversions", "conversion" -> "CONVERSIONS";
            case "website traffic" -> "WEBSITE TRAFFIC";
            default -> rawGoal.toUpperCase(Locale.ROOT);
        };
        return new Resolved(label + " (auto: Proposal Goal column)", mapped, "sheet");
    }

    public static Resolved resolveTacticOverview(int n, List<List<String>> sheetRows,
                                                 List<List<String>> adjRows, ClaudeResults cc) {
        String label = "Tactic " + n + " overview:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        String generated = cc == null || cc.tacticOverviews() == null ? null : cc.tacticOverviews().get(n);
        if (generated != null) {
            return new Resolved(label + " (auto: Claude)", generated, "adj");
        }
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticTopCreativeName(int n, List<List<String>> sheetRows,
                                                        List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " top creative name:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        String val = t == null ? null : t.topCreativeName();
        if (val != null) return new Resolved(label + " (auto: BQ top imps)", val, "adj");
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticTopCreativeImps(int n, List<List<String>> sheetRows,
                                                        List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " top creative imps:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        Double val = t == null ? null : t.topCreativeImps();
        if (val != null) return new Resolved(label + " (auto: BQ top imps)", Fmt.intGroup(val), "adj");
        return Resolved.notFound(label);
    }

    public static Resolved resolveTacticTopCreativeClicks(int n, List<List<String>> sheetRows,
                                                          List<List<String>> adjRows, CampaignData data) {
        String label = "Tactic " + n + " top creative clicks:";
        String fromAdj = SheetUtils.findLabelValue(adjRows, label);
        if (fromAdj != null) return new Resolved(label, fromAdj, "adj");
        String fromSheet = SheetUtils.findLabelValue(sheetRows, label);
        if (fromSheet != null) return new Resolved(label, fromSheet, "sheet");
        CampaignData.Tactic t = tactic(data, n);
        Double val = t == null ? null : t.topCreativeClicks();
        if (val != null) return new Resolved(label + " (auto: BQ top creative)", Fmt.intGroup(val), "adj");
        return Resolved.notFound(label);
    }

    /** {@code gender} is {@code "male"} or {@code "female"}; adj → sheet → Claude Batch B. */
    public static Resolved resolveTacticGender(int n, String gender, List<List<String>> sheetRows,
                                               List<List<String>> adjRows, ClaudeTactical ccB) {
        String label = "Tactic " + n + " " + gender + ":";
        Resolved manual = CampaignResolvers.resolve(sheetRows, adjRows, label);
        if (manual.found()) return manual;
        ClaudeTactical.TacticInsight ti = ccB == null ? null : ccB.get(n);
        if (ti != null) {
            int val = "male".equals(gender) ? ti.male() : ti.female();
            return new Resolved(label + " (auto: Claude)", val + "%", "adj");
        }
        return Resolved.notFound(label);
    }

    /** {@code part} is {@code "weekdays"} or {@code "weekends"}; adj → sheet → Claude Batch B. */
    public static Resolved resolveTacticDaypart(int n, String part, List<List<String>> sheetRows,
                                                List<List<String>> adjRows, ClaudeTactical ccB) {
        String label = "Tactic " + n + " " + part + ":";
        Resolved manual = CampaignResolvers.resolve(sheetRows, adjRows, label);
        if (manual.found()) return manual;
        ClaudeTactical.TacticInsight ti = ccB == null ? null : ccB.get(n);
        if (ti != null) {
            String val = "weekdays".equals(part) ? ti.weekdays() : ti.weekends();
            if (val != null) return new Resolved(label + " (auto: Claude)", val, "adj");
        }
        return Resolved.notFound(label);
    }

    private static CampaignData.Tactic tactic(CampaignData data, int n) {
        Map<Integer, CampaignData.Tactic> tactics = data == null ? null : data.tactics();
        return tactics == null ? null : tactics.get(n);
    }

    private static String cell(List<String> row, int idx) {
        if (row == null || idx < 0 || idx >= row.size() || row.get(idx) == null) return "";
        return row.get(idx).trim();
    }

    private static String cellAt(List<String> row, int idx) {
        return cell(row, idx);
    }

    private static String joinLower(List<String> row, int n) {
        if (row == null) return "";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(n, row.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(' ');
            String c = row.get(i);
            sb.append(c == null ? "" : c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
