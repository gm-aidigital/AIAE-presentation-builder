package com.aidigital.reportconstructor.service.reports.engine;

import org.springframework.stereotype.Component;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload.LineItemMapping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java port of {@code api/placeholders/collector.php} — {@code collectCampaignData()}.
 *
 * <p>Single pass over the BigQuery export ({@code adjRows}) to compute campaign
 * totals, per-channel and per-line-item aggregates, weekday/weekend split and the
 * top delivery creative; plus a parse of the Estimates tab for planned KPIs. The
 * result mirrors the PHP array consumed by the resolvers and Claude batches.
 */
@Component
public class CampaignDataCollector {
    private final SheetUtils sheetUtils;
    private final TacticUtils tacticUtils;
    private final CampaignResolvers campaignResolvers;

    public CampaignDataCollector(
            SheetUtils sheetUtils, TacticUtils tacticUtils, CampaignResolvers campaignResolvers) {
        this.sheetUtils = sheetUtils;
        this.tacticUtils = tacticUtils;
        this.campaignResolvers = campaignResolvers;
    }

    private static final String[] STOP_WORDS = {"added value", "totals", "please note", "total:"};

    /** Mutable per-key accumulator (channel or line-item). */
    private static final class Agg {
        double spend;
        double imps;
        double clicks;
        double completions;
        double weekdayImps;
        double weekendImps;
        boolean hasCompletions;
    }

    public CampaignData collect(
        List<List<String>> sheetRows,
        List<List<String>> adjRows,
        List<List<String>> audienceRows,
        List<List<String>> estimatesRows,
        List<LineItemMapping> lineItemMapping
    ) {
        if (sheetRows == null) sheetRows = List.of();
        if (adjRows == null) adjRows = List.of();
        if (audienceRows == null) audienceRows = List.of();
        if (estimatesRows == null) estimatesRows = List.of();
        if (lineItemMapping == null) lineItemMapping = List.of();

        // ── 1. Campaign fields: adj overrides sheet ───────────────────────────
        String client   = coalesce(sheetUtils.findLabelValue(adjRows, "Client name:"), sheetUtils.findLabelValue(sheetRows, "Client name:"));
        String campaign = coalesce(sheetUtils.findLabelValue(adjRows, "Campaign:"), sheetUtils.findLabelValue(sheetRows, "Campaign:"));
        String geo      = coalesce(sheetUtils.findLabelValue(adjRows, "Geo locations:"),
                          coalesce(sheetUtils.findLabelValue(sheetRows, "Geo locations:"),
                                   sheetUtils.findLabelValueBelow(sheetRows, "Geo")));
        String goal     = coalesce(sheetUtils.findLabelValue(adjRows, "Funnel stages:"),
                          coalesce(sheetUtils.findLabelValue(sheetRows, "Funnel stages:"),
                                   sheetUtils.findLabelValueBelow(sheetRows, "Goal")));
        String budget   = coalesce(sheetUtils.findLabelValue(adjRows, "Total investment:"), sheetUtils.findLabelValue(sheetRows, "Total investment:"));
        String kpis     = coalesce(sheetUtils.findLabelValue(adjRows, "Primary KPIs:"), sheetUtils.findLabelValue(sheetRows, "Primary KPIs:"));

        // ── 2. Flight ─────────────────────────────────────────────────────────
        FlightDates flightTs = sheetUtils.resolveFlightTimestamps(sheetRows, adjRows);
        String flightDates = flightTs != null ? sheetUtils.formatFlightDates(flightTs.start(), flightTs.end()) : null;

        // ── 3. Tactics list ───────────────────────────────────────────────────
        String tacticsList = campaignResolvers.resolveTacticsList(sheetRows, adjRows).value();

        // ── 4. Explicit audience fields ───────────────────────────────────────
        String audienceAge  = coalesce(sheetUtils.findLabelValue(adjRows, "Audience age:"), sheetUtils.findLabelValue(sheetRows, "Audience age:"));
        String audienceSegs = coalesce(sheetUtils.findLabelValue(adjRows, "Audience segments:"), sheetUtils.findLabelValue(sheetRows, "Audience segments:"));

        // ── 5. Estimates tab → planned KPIs by tactic ─────────────────────────
        Map<String, double[]> estimatesByTactic = parseEstimates(estimatesRows);
        // double[] layout: {spend, imps, ctr, vcr, maxFreq}; NaN = null

        // ── 6. Tactics & channel mapping ──────────────────────────────────────
        List<String> mediaTactics = tacticUtils.extractTacticsFromMedia(sheetRows);
        Map<Integer, String[]> tacticMap = new LinkedHashMap<>(); // N -> [name, channel|null]
        for (int n = 1; n <= 7; n++) {
            String name = coalesce(sheetUtils.findLabelValue(adjRows, "Tactic " + n + ":"),
                          coalesce(sheetUtils.findLabelValue(sheetRows, "Tactic " + n + ":"),
                                   n - 1 < mediaTactics.size() ? mediaTactics.get(n - 1) : null));
            if (name == null) continue;
            tacticMap.put(n, new String[]{name, tacticUtils.getTacticChannelFilter(name)});
        }

        // Join line items to tactics by tactic_num carried in the mapping payload,
        // exactly like PHP collector.php. The tactic NAME is never used for the join:
        // an Adjustments/sheet "Tactic N:" override renames the tactic but must not
        // break the line-item match. liToTacticNum gates row aggregation (presence of
        // the id in the mapping); numToLiId resolves the id for each tactic position.
        Map<String, Integer> liToTacticNum = new LinkedHashMap<>();
        Map<Integer, String> numToLiId = new LinkedHashMap<>();
        for (LineItemMapping m : lineItemMapping) {
            String id = m.lineItemId() == null ? "" : m.lineItemId().trim();
            if (id.isEmpty()) continue;
            int num = m.tacticNum() == null ? 0 : m.tacticNum();
            liToTacticNum.put(id, num);
            if (num > 0) numToLiId.putIfAbsent(num, id);
        }

        // ── 6b. Single pass over adjRows ──────────────────────────────────────
        Agg totals = new Agg();
        double[] impsWithCompletions = {0.0};
        Map<String, Agg> byChannel = new LinkedHashMap<>();
        Map<String, Agg> byLineItemId = new LinkedHashMap<>();
        Map<String, Map<String, double[]>> byCreative = new LinkedHashMap<>(); // liId -> creative -> {imps, clicks}

        int hIdx = -1, colDt = -1, colCh = -1, colCo = -1, colIm = -1, colCl = -1, colCmp = -1, colDow = -1, colLi = -1, colCr = -1;
        for (int i = 0; i < adjRows.size(); i++) {
            List<String> row = adjRows.get(i);
            if (row == null) continue;
            Map<String, Integer> f = new LinkedHashMap<>();
            for (int j = 0; j < row.size(); j++) {
                String v = cell(row, j).toLowerCase(Locale.ROOT);
                switch (v) {
                    case "date" -> f.put("date", j);
                    case "channel" -> f.put("channel", j);
                    case "cost" -> f.put("cost", j);
                    case "impressions" -> f.put("imps", j);
                    case "clicks" -> f.put("clicks", j);
                    case "completions" -> f.put("completions", j);
                    default -> { }
                }
                if (v.equals("day_of_week") || v.equals("dayofweek") || v.equals("day")) f.put("dow", j);
                if (v.equals("line item id") || v.equals("line_item_id") || v.equals("lineitemid") || v.equals("line item")) f.put("li", j);
                if (v.equals("creative") || v.equals("creative name") || v.equals("creative_name")) f.put("creative", j);
            }
            if (f.containsKey("date") && f.containsKey("channel") && f.containsKey("cost") && f.containsKey("imps")) {
                hIdx = i;
                colDt = f.get("date"); colCh = f.get("channel"); colCo = f.get("cost"); colIm = f.get("imps");
                colCl = f.getOrDefault("clicks", -1);
                colCmp = f.getOrDefault("completions", -1);
                colDow = f.getOrDefault("dow", -1);
                colLi = f.getOrDefault("li", -1);
                colCr = f.getOrDefault("creative", -1);
                break;
            }
        }

        int colL1Naming = -1;
        if (colLi < 0 && hIdx >= 0) {
            List<String> hdr = adjRows.get(hIdx);
            for (int j = 0; j < hdr.size(); j++) {
                if (cell(hdr, j).toLowerCase(Locale.ROOT).contains("level 1 naming")) { colL1Naming = j; break; }
            }
        }

        if (hIdx >= 0) {
            LocalDate dayStart = flightTs != null ? flightTs.start() : null;
            LocalDate dayEnd = flightTs != null ? flightTs.end() : null;

            for (int i = hIdx + 1; i < adjRows.size(); i++) {
                List<String> row = adjRows.get(i);
                if (row == null) continue;

                String dateVal = cellAt(row, colDt);
                if (dateVal.isEmpty()) continue;
                LocalDate ts = sheetUtils.parseDate(dateVal);
                if (ts == null) continue;
                if (dayStart != null && (ts.isBefore(dayStart) || ts.isAfter(dayEnd))) continue;

                String chVal = cellAt(row, colCh).toLowerCase(Locale.ROOT);

                String liId = null;
                if (colLi >= 0) {
                    String v = cellAt(row, colLi);
                    if (!v.isEmpty()) liId = v;
                } else if (colL1Naming >= 0) {
                    String naming = cellAt(row, colL1Naming);
                    if (!naming.isEmpty()) {
                        String[] parts = naming.split("_", -1);
                        String candidate = parts.length > 8 ? parts[8].trim() : "";
                        if (!candidate.isEmpty() && !candidate.equals("-") && candidate.chars().allMatch(Character::isDigit)) {
                            liId = candidate;
                        }
                    }
                }

                double co = toFloat(cleanNum(cellAt(row, colCo), true));
                double im = toFloat(cleanNum(cellAt(row, colIm), false));
                double cl = colCl >= 0 ? toFloat(cleanNum(cellAt(row, colCl), false)) : 0.0;
                double cmp = colCmp >= 0 ? toFloat(cleanNum(cellAt(row, colCmp), false)) : 0.0;

                boolean isWeekend;
                if (colDow >= 0) {
                    String dowVal = cellAt(row, colDow).toLowerCase(Locale.ROOT);
                    if (isNumeric(dowVal)) {
                        int d = (int) toFloat(dowVal);
                        isWeekend = d == 0 || d == 6 || d == 7;
                    } else {
                        isWeekend = dowVal.equals("saturday") || dowVal.equals("sunday") || dowVal.equals("sat") || dowVal.equals("sun");
                    }
                } else {
                    DayOfWeek dow = ts.getDayOfWeek();
                    isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                }

                totals.spend += co;
                totals.imps += im;
                totals.clicks += cl;
                if (colCmp >= 0 && cmp > 0) {
                    totals.completions += cmp;
                    impsWithCompletions[0] += im;
                    totals.hasCompletions = true;
                }

                if (!chVal.isEmpty()) {
                    Agg a = byChannel.computeIfAbsent(chVal, k -> new Agg());
                    a.spend += co; a.imps += im; a.clicks += cl;
                    if (cmp > 0) { a.completions += cmp; a.hasCompletions = true; }
                    if (isWeekend) a.weekendImps += im; else a.weekdayImps += im;
                }

                if (liId != null && liToTacticNum.containsKey(liId)) {
                    Agg a = byLineItemId.computeIfAbsent(liId, k -> new Agg());
                    a.spend += co; a.imps += im; a.clicks += cl;
                    if (cmp > 0) { a.completions += cmp; a.hasCompletions = true; }
                    if (isWeekend) a.weekendImps += im; else a.weekdayImps += im;

                    if (colCr >= 0) {
                        String crName = cellAt(row, colCr);
                        if (!crName.isEmpty()) {
                            double[] cr = byCreative.computeIfAbsent(liId, k -> new LinkedHashMap<>())
                                .computeIfAbsent(crName, k -> new double[]{0.0, 0.0});
                            cr[0] += im; cr[1] += cl;
                        }
                    }
                }
            }
        }

        // ── 6c. Top creative per line item ────────────────────────────────────
        Map<String, double[]> topCreativeByLi = new LinkedHashMap<>(); // liId -> {imps, clicks}
        Map<String, String> topCreativeName = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, double[]>> e : byCreative.entrySet()) {
            String topName = null;
            double topImps = -1.0, topClicks = 0.0;
            for (Map.Entry<String, double[]> c : e.getValue().entrySet()) {
                if (c.getValue()[0] > topImps) {
                    topImps = c.getValue()[0];
                    topClicks = c.getValue()[1];
                    topName = c.getKey();
                }
            }
            if (topName != null) {
                topCreativeByLi.put(e.getKey(), new double[]{topImps, topClicks});
                topCreativeName.put(e.getKey(), topName);
            }
        }

        // ── 7. Totals CTR/VCR ─────────────────────────────────────────────────
        Double totalCtr = totals.imps > 0 ? totals.clicks / totals.imps * 100 : null;
        Double totalVcr = (totals.hasCompletions && impsWithCompletions[0] > 0)
            ? totals.completions / impsWithCompletions[0] * 100 : null;

        // ── 8. Per-tactic metrics ─────────────────────────────────────────────
        Map<Integer, CampaignData.Tactic> tacticsData = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> e : tacticMap.entrySet()) {
            int n = e.getKey();
            String name = e.getValue()[0];
            String channel = e.getValue()[1];

            String liIdForTactic = numToLiId.get(n);
            Agg agg = null;
            if (liIdForTactic != null && byLineItemId.containsKey(liIdForTactic)) {
                agg = byLineItemId.get(liIdForTactic);
            } else {
                String ch = channel != null ? channel.trim().toLowerCase(Locale.ROOT) : null;
                if (ch != null && byChannel.containsKey(ch)) agg = byChannel.get(ch);
            }

            double sp = agg != null ? agg.spend : 0.0;
            double im = agg != null ? agg.imps : 0.0;
            double cl = agg != null ? agg.clicks : 0.0;
            double cmp = agg != null ? agg.completions : 0.0;
            double wdi = agg != null ? agg.weekdayImps : 0.0;
            double wei = agg != null ? agg.weekendImps : 0.0;

            Double tCtr = im > 0 ? cl / im * 100 : null;
            Double tVcr = (agg != null && agg.hasCompletions && im > 0) ? cmp / im * 100 : null;

            double totalDayImps = wdi + wei;
            Integer weekdaysPct = totalDayImps > 0 ? (int) Math.round(wdi / totalDayImps * 100) : null;
            Integer weekendsPct = weekdaysPct != null ? 100 - weekdaysPct : null;

            double[] plan = estimatesByTactic.get(name.trim().toLowerCase(Locale.ROOT));

            String topName = liIdForTactic != null ? topCreativeName.get(liIdForTactic) : null;
            double[] topCr = liIdForTactic != null ? topCreativeByLi.get(liIdForTactic) : null;

            tacticsData.put(n, new CampaignData.Tactic(
                name,
                channel,
                liIdForTactic,
                sp, im, cl, cmp,
                tCtr, tVcr,
                weekdaysPct, weekendsPct,
                plan != null ? nan(plan[0]) : null,
                plan != null ? nan(plan[1]) : null,
                plan != null ? nan(plan[2]) : null,
                plan != null ? nan(plan[3]) : null,
                plan != null ? nan(plan[4]) : null,
                topName,
                topCr != null ? topCr[0] : null,
                topCr != null ? topCr[1] : null
            ));
        }

        // ── 9. Audience tab text (Batch A) ────────────────────────────────────
        List<String> audLines = new ArrayList<>();
        int aLimit = Math.min(200, audienceRows.size());
        for (int i = 0; i < aLimit; i++) {
            List<String> row = audienceRows.get(i);
            if (row == null) continue;
            List<String> cells = new ArrayList<>();
            for (String c : row) {
                String t = c == null ? "" : c.trim();
                if (!t.isEmpty()) cells.add(t);
            }
            if (!cells.isEmpty()) audLines.add(String.join(" | ", cells));
        }
        String audienceTabText = String.join("\n", audLines);

        return new CampaignData(
            client, campaign, geo, goal, flightDates, flightTs, budget, kpis, tacticsList,
            audienceAge, audienceSegs,
            new CampaignData.Totals(totals.spend, totals.imps, totals.clicks, totals.completions, totalCtr, totalVcr),
            tacticsData,
            audienceTabText
        );
    }

    // ── Estimates parser ──────────────────────────────────────────────────────

    Map<String, double[]> parseEstimates(List<List<String>> estimatesRows) {
        Map<String, double[]> out = new LinkedHashMap<>();
        if (estimatesRows.isEmpty()) return out;

        int eHdrIdx = -1, eMediaCol = -1, eCostCol = -1, eImpsCol = -1, eCtrCol = -1, eVcrCol = -1, eFreqCol = -1;
        for (int i = 0; i < estimatesRows.size(); i++) {
            List<String> row = estimatesRows.get(i);
            if (row == null) continue;
            boolean hasMedia = false;
            int media = -1, cost = -1, imps = -1, ctr = -1, vcr = -1, freq = -1;
            for (int j = 0; j < row.size(); j++) {
                String v = cell(row, j).toLowerCase(Locale.ROOT);
                if (v.equals("media")) { hasMedia = true; media = j; }
                if (v.equals("total cost") || v.equals("cost") || v.equals("budget")) cost = j;
                if (v.equals("impressions") || v.equals("imps")) imps = j;
                if (v.equals("ctr")) ctr = j;
                if (v.equals("vcr") || v.equals("vtr") || v.equals("view rate") || v.equals("vcr / acr") || v.equals("vcr/acr") || v.equals("acr")) vcr = j;
                if (v.contains("max frequency") || v.contains("frequency per flight")) freq = j;
            }
            if (hasMedia && (cost >= 0 || imps >= 0)) {
                eHdrIdx = i; eMediaCol = media; eCostCol = cost; eImpsCol = imps; eCtrCol = ctr; eVcrCol = vcr; eFreqCol = freq;
                break;
            }
        }
        if (eHdrIdx < 0) return out;

        for (int i = eHdrIdx + 1; i < estimatesRows.size(); i++) {
            List<String> row = estimatesRows.get(i);
            if (row == null) continue;
            String rowText = joinLower(row, 5);
            boolean stop = false;
            for (String sw : STOP_WORDS) {
                if (rowText.contains(sw)) { stop = true; break; }
            }
            if (stop) break;

            String mediaVal = cellAt(row, eMediaCol);
            if (mediaVal.isEmpty()) continue;

            double spend = parseNum(cellAt(row, eCostCol), eCostCol, true);
            double imps = parseNum(cellAt(row, eImpsCol), eImpsCol, false);
            double ctr = parseNum(cellAt(row, eCtrCol), eCtrCol, false);
            double vcr = parseNum(cellAt(row, eVcrCol), eVcrCol, false);
            double freq = parseNum(cellAt(row, eFreqCol), eFreqCol, false);

            out.put(mediaVal.toLowerCase(Locale.ROOT), new double[]{spend, imps, ctr, vcr, freq});
        }
        return out;
    }

    /** Cleans then parses a cell to a numeric, returning {@code NaN} when blank/non-numeric. */
    double parseNum(String raw, int col, boolean allowMinus) {

        if (col < 0) return Double.NaN;
        String c = cleanNum(raw, allowMinus);
        return !c.isEmpty() && isNumeric(c) ? toFloat(c) : Double.NaN;
    }

    Double nan(double v) {

        return Double.isNaN(v) ? null : v;
    }

    // ── numeric helpers (mirror PHP cleanup + (float) cast + is_numeric) ───────

    private static final Pattern LEADING_NUM = Pattern.compile("^[-+]?\\d*\\.?\\d+");

    String cleanNum(String raw, boolean allowMinus) {

        if (raw == null) return "";
        String s = raw.replace(",", "");
        return s.replaceAll(allowMinus ? "[^0-9.\\-]" : "[^0-9.]", "");
    }

    double toFloat(String s) {

        if (s == null) return 0.0;
        Matcher m = LEADING_NUM.matcher(s.trim());
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    boolean isNumeric(String s) {

        return s != null && s.matches("[-+]?\\d*\\.?\\d+");
    }

    // ── cell helpers ──────────────────────────────────────────────────────────

    String coalesce(String a, String b) {

        return a != null ? a : b;
    }

    String cell(List<String> row, int idx) {

        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    String cellAt(List<String> row, int idx) {

        if (row == null || idx < 0 || idx >= row.size()) return "";
        return cell(row, idx);
    }

    String joinLower(List<String> row, int n) {

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(n, row.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(' ');
            sb.append(cell(row, i));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
