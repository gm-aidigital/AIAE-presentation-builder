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
     * Batch A {@code proposal_overview}: window limit+120, last {@code .} threshold limit*0.5.
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
            int lp = window.lastIndexOf('.');
            if (lp >= (int) (limit * 0.5)) {
                val = val.substring(0, lp + 1).trim();
            } else {
                String cut = val.substring(0, limit);
                int ls = cut.lastIndexOf(' ');
                val = ls >= 0 ? val.substring(0, ls).trim() : cut.trim();
            }
        }
        return val.isEmpty() ? null : val;
    }

    /**
     * Batch C normalize: window=limit, last {@code .}/{@code ,} threshold limit*0.75.
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
            int lp = Math.max(cut.lastIndexOf('.'), cut.lastIndexOf(','));
            val = lp > (int) (limit * 0.75) ? val.substring(0, lp + 1).trim() : cut.trim();
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

    /** Returns textual node value or {@code null} when absent/empty. */
    public String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.isTextual() ? node.asText() : node.toString();
        return s == null || s.isEmpty() ? null : s;
    }

    /** True when the string has non-whitespace content. */
    public boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Batch A audience_segments — max 80 chars, cut at last comma when over limit. */
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

    /** Batch A strategic point — hard max 22 characters. */
    public String limitStrategicPoint(String point) {
        if (point == null) {
            return "";
        }
        point = point.trim();
        return point.length() > 22 ? point.substring(0, 22) : point;
    }

    /** Batch A strategic overview — max 240 chars, cut at last {@code .} or {@code ,} past 180. */
    public String limitStrategicOverview(String overview) {
        if (overview == null) {
            return "";
        }
        overview = overview.trim();
        if (overview.length() > 240) {
            String cut = overview.substring(0, 240);
            int lp = Math.max(cut.lastIndexOf('.'), cut.lastIndexOf(','));
            overview = lp > 180 ? overview.substring(0, lp + 1).trim() : cut.trim();
        }
        return overview;
    }

    /** Batch C results_overview — {@link #normalizeC} with limit 380. */
    public String limitResultsOverview(String val) {
        return normalizeC(val, 380);
    }

    /** Batch C tactic_overview — {@link #normalizeC} with limit 210. */
    public String limitTacticOverview(String val) {
        return normalizeC(val, 210);
    }

    /** Geo tab summary — max 40 characters after whitespace collapse. */
    public String limitGeoSummary(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.replaceAll("\\s*[\\r\\n]+\\s*", " ").replaceAll("\\s{2,}", " ").trim();
        if (text.length() > 40) {
            text = text.substring(0, 40).trim();
        }
        return text.isEmpty() ? null : text;
    }
}
