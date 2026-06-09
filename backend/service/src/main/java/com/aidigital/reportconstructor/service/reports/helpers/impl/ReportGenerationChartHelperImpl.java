package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationChartHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link ReportGenerationChartHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationChartHelperImpl implements ReportGenerationChartHelper {

    private static final Pattern PRESENTATION_ID = Pattern.compile("/d/([a-zA-Z0-9_-]+)");

    private final ChartProvider charts;
    private final SlidesProvider slides;
    private final TacticExtractionHelper tacticExtraction;
    private final ReportNumberParser reportNumbers;

    @Override
    public List<String> buildCharts(
        String slideUrl,
        GeneratePayload payload,
        CampaignData data,
        Map<String, String> flatReplacements,
        String userGoogleToken
    ) {
        if (payload.bqSheetId() == null || payload.bqSheetId().isBlank()
            || payload.adjRows() == null || payload.adjRows().isEmpty()
            || payload.lineItemMapping() == null || payload.lineItemMapping().isEmpty()) {
            return List.of();
        }
        String presentationId = extractPresentationId(slideUrl);
        if (presentationId == null) {
            return List.of("Charts skipped — could not determine presentation id from " + slideUrl);
        }

        int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);
        String campaignTitle = firstNonBlank(
            flatReplacements.get("{{Campaign_name}}"),
            flatReplacements.get("{{client_name}}"),
            "Campaign"
        );

        Map<Integer, String> distNames = new LinkedHashMap<>();
        Map<Integer, Double> distImps = new LinkedHashMap<>();
        for (int n = 1; n <= tacticCount; n++) {
            distNames.put(n, firstNonBlank(flatReplacements.get("{{tactic " + n + "}}"), "Tactic " + n));
            distImps.put(n, reportNumbers.parseReportNumber(flatReplacements.get("{{tactic " + n + " imps}}")));
        }
        double totalImps = reportNumbers.parseReportNumber(flatReplacements.get("{{total imps}}"));

        try {
            return charts.buildCharts(new ChartProvider.ChartRequest(
                presentationId,
                payload.adjRows(),
                payload.lineItemMapping(),
                data.flightTs(),
                tacticCount,
                campaignTitle,
                distNames,
                distImps,
                totalImps,
                userGoogleToken
            ));
        } catch (RuntimeException ex) {
            log.error("[charts] chart step failed for presentation {}", presentationId, ex);
            return List.of("Charts failed: " + ex.getMessage());
        }
    }

    @Override
    public void trimUnusedTactics(String slideUrl, GeneratePayload payload, String userGoogleToken) {
        String presentationId = extractPresentationId(slideUrl);
        if (presentationId == null) {
            return;
        }
        int tacticCount = Math.clamp(tacticExtraction.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);
        try {
            slides.trimTactics(presentationId, tacticCount, userGoogleToken);
        } catch (RuntimeException ex) {
            log.warn("[slides] trimTactics failed for {} (non-fatal): {}", presentationId, ex.getMessage());
        }
    }

    String extractPresentationId(String slideUrl) {
        if (slideUrl == null) {
            return null;
        }
        Matcher m = PRESENTATION_ID.matcher(slideUrl);
        return m.find() ? m.group(1) : null;
    }

    String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"—".equals(v.trim())) {
                return v.trim();
            }
        }
        return "";
    }
}
