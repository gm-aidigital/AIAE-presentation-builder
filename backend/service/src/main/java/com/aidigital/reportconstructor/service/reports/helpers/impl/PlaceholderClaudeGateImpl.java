package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderClaudeGate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Spring bean implementation of {@link PlaceholderClaudeGate}.
 */
@Component
@RequiredArgsConstructor
public class PlaceholderClaudeGateImpl implements PlaceholderClaudeGate {

    private final SheetRowHelper sheetUtils;

    @Override
    public boolean needStrategic(GeneratePayload payload) {
        List<List<String>> adj = payload.adjRows();
        List<List<String>> sheet = payload.sheetRows();
        if (bothNull(adj, sheet, "Audience age:")) {
            return true;
        }
        if (bothNull(adj, sheet, "Audience segments:")) {
            return true;
        }
        if (bothNull(adj, sheet, "Proposal overview:")) {
            return true;
        }
        for (int i = 1; i <= 4; i++) {
            if (bothNull(adj, sheet, "Strategic point " + i + ":")) {
                return true;
            }
            if (bothNull(adj, sheet, "Strategic overview " + i + ":")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean needTactical(GeneratePayload payload, CampaignData data) {
        if (data == null || data.tactics() == null) {
            return false;
        }
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

    @Override
    public boolean needResults(GeneratePayload payload, CampaignData data) {
        List<List<String>> adj = payload.adjRows();
        List<List<String>> sheet = payload.sheetRows();
        if (bothNull(adj, sheet, "Our results overview:")) {
            return true;
        }
        if (bothNull(adj, sheet, "Thoughts on the performance:")) {
            return true;
        }
        if (data != null && data.tactics() != null) {
            for (int n : data.tactics().keySet()) {
                if (bothNull(adj, sheet, "Tactic " + n + " overview:")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean needGeoSummary(GeneratePayload payload) {
        List<List<String>> adj = payload.adjRows();
        List<List<String>> sheet = payload.sheetRows();
        if (sheetUtils.findLabelValue(adj, "Geo locations:") != null) {
            return false;
        }
        if (sheetUtils.findLabelValue(sheet, "Geo locations:") != null) {
            return false;
        }
        String below = sheetUtils.findLabelValueBelow(sheet, "Geo");
        if (below == null) {
            return false;
        }
        String lc = below.toLowerCase(Locale.ROOT);
        return lc.contains("see geo tab") || lc.contains("geo tab");
    }

    boolean bothNull(List<List<String>> adj, List<List<String>> sheet, String label) {
        return sheetUtils.findLabelValue(adj, label) == null
            && sheetUtils.findLabelValue(sheet, label) == null;
    }
}
