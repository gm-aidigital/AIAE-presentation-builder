package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot.Headers;
import com.aidigital.reportconstructor.service.reports.engine.ChartPivot.Pivot;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider.ChartRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ChartSpec;
import com.google.api.services.slides.v1.Slides;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live port of PHP {@code api/chart_builder.php}: orchestrates daily/monthly combo
 * charts and distribution pies via injected Google helpers.
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class RealChartProvider implements ChartProvider {

    private final GoogleClientsFactory clients;
    private final ChartPivot chartPivot;
    private final ChartSheetWriter chartSheetWriter;
    private final ChartSpecBuilder chartSpecBuilder;
    private final SlideChartSwapper slideChartSwapper;
    private final DriveCopier driveCopier;
    private final ChartErrorTranslator chartErrors;
    private final ChartTemplateCatalog templates;

    public RealChartProvider(
            GoogleClientsFactory clients,
            ChartPivot chartPivot,
            ChartSheetWriter chartSheetWriter,
            ChartSpecBuilder chartSpecBuilder,
            SlideChartSwapper slideChartSwapper,
            DriveCopier driveCopier,
            ChartErrorTranslator chartErrors,
            ChartTemplateCatalog templates) {
        this.clients = clients;
        this.chartPivot = chartPivot;
        this.chartSheetWriter = chartSheetWriter;
        this.chartSpecBuilder = chartSpecBuilder;
        this.slideChartSwapper = slideChartSwapper;
        this.driveCopier = driveCopier;
        this.chartErrors = chartErrors;
        this.templates = templates;
        log.info("[charts] live chart provider initialised");
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public List<String> buildCharts(ChartRequest req) {
        boolean asUser = req.userGoogleAccessToken() != null && !req.userGoogleAccessToken().isBlank();
        HttpRequestInitializer init = asUser
            ? clients.userInitializer(req.userGoogleAccessToken())
            : clients.serviceAccountInitializer();
        Drive drive = clients.drive(init);
        Sheets sheets = clients.sheets(init);
        Slides slides = clients.slides(init);

        log.info("[charts] presentation {} → building charts for {} tactic(s) under {}",
            req.presentationId(), req.tacticCount(), asUser ? "the signed-in user" : "the service account");

        List<String> errors = new ArrayList<>();

        String folderId = null;
        try {
            folderId = driveCopier.createFolder(drive, "Charts — " + req.campaignTitle());
        } catch (IOException ex) {
            log.warn("[charts] could not create chart folder, copies go to root: {}", ex.getMessage());
        }

        Headers headers = chartPivot.parseBqHeaders(req.bqRows());

        errors.addAll(buildDailyCharts(drive, sheets, slides, req, headers, folderId));
        errors.addAll(buildMonthlyCharts(drive, sheets, slides, req, headers, folderId));
        errors.addAll(buildDistributionCharts(drive, sheets, slides, req, folderId));

        log.info("[charts] presentation {} → done with {} error(s)", req.presentationId(), errors.size());
        return errors;
    }

    private List<String> buildDailyCharts(
            Drive drive, Sheets sheets, Slides slides, ChartRequest req, Headers headers, String folderId) {
        List<String> errors = new ArrayList<>();
        if (!headers.valid()) {
            errors.add("Daily: BQ sheet — Date or Impressions column not found");
            return errors;
        }
        Map<Integer, List<String>> tacticLineItems = tacticLineItems(req.lineItemMapping());
        Map<String, SlideChartSwapper.ElementTransform> transforms =
            slideChartSwapper.loadTransforms(slides, req.presentationId(), errors, "Daily");

        for (int n = 1; n <= req.tacticCount(); n++) {
            List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
            try {
                Pivot pivot = chartPivot.buildDailyPivot(req.bqRows(), liIds, headers, req.flightTs());
                if (pivot.isEmpty()) {
                    errors.add("Tactic " + n + ": no BQ data (line item ids: " + String.join(",", liIds) + ")");
                    continue;
                }
                renderTacticChart(drive, sheets, slides, req.presentationId(),
                    templates.getDailyTemplateSheetIds().get(n),
                    templates.getDailySlideObjectIds().get(n),
                    "Chart Tactic " + n + " — " + req.campaignTitle(), folderId, pivot, transforms,
                    "Tactic " + n, errors);
            } catch (IOException ex) {
                errors.add(chartErrors.describeChartError("Tactic " + n, ex));
            }
        }
        return errors;
    }

    private List<String> buildMonthlyCharts(
            Drive drive, Sheets sheets, Slides slides, ChartRequest req, Headers headers, String folderId) {
        List<String> errors = new ArrayList<>();
        if (!headers.valid()) {
            errors.add("Monthly: BQ sheet — Date or Impressions column not found");
            return errors;
        }
        Map<Integer, List<String>> tacticLineItems = tacticLineItems(req.lineItemMapping());
        boolean multiYear = chartPivot.isMultiYear(req.bqRows(), headers, req.flightTs());
        Map<String, SlideChartSwapper.ElementTransform> transforms =
            slideChartSwapper.loadTransforms(slides, req.presentationId(), errors, "Monthly");

        for (int n = 1; n <= req.tacticCount(); n++) {
            List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
            try {
                Pivot pivot = chartPivot.buildMonthlyPivot(req.bqRows(), liIds, headers, req.flightTs(), multiYear);
                if (pivot.isEmpty()) {
                    errors.add("Monthly Tactic " + n + ": no data (line item ids: " + String.join(",", liIds) + ")");
                    continue;
                }
                renderTacticChart(drive, sheets, slides, req.presentationId(),
                    templates.getMonthlyTemplateSheetIds().get(n),
                    templates.getMonthlySlideObjectIds().get(n),
                    "Monthly Chart Tactic " + n + " — " + req.campaignTitle(), folderId, pivot, transforms,
                    "Monthly Tactic " + n, errors);
            } catch (IOException ex) {
                errors.add(chartErrors.describeChartError("Monthly Tactic " + n, ex));
            }
        }
        return errors;
    }

    private List<String> buildDistributionCharts(
            Drive drive, Sheets sheets, Slides slides, ChartRequest req, String folderId) {
        List<String> errors = new ArrayList<>();
        Map<String, SlideChartSwapper.ElementTransform> transforms =
            slideChartSwapper.loadTransforms(slides, req.presentationId(), errors, "Distribution");

        for (int n = 1; n <= req.tacticCount(); n++) {
            String tacticName = req.distTacticNames().getOrDefault(n, "Tactic " + n);
            double tacticImp = req.distTacticImps().getOrDefault(n, 0.0);
            double otherImps = req.distTotalImps();
            String templateId = templates.getDistTemplateSheetIds().get(n);
            String oldObjectId = templates.getDistSlideObjectIds().get(n);
            try {
                String copiedId = driveCopier.copyFile(drive, templateId,
                    "Distribution Chart Tactic " + n + " — " + req.campaignTitle(), folderId);
                ChartSpec spec = chartSpecBuilder.readChartSpec(sheets, templateId);
                String tab = chartSpecBuilder.findDataTab(sheets, copiedId);
                chartSheetWriter.writeDistribution(sheets, copiedId, tab, tacticName, tacticImp, otherImps);
                if (spec != null) {
                    try {
                        chartSpecBuilder.applyChartSpec(sheets, copiedId, chartSpecBuilder.injectPieSliceColors(spec));
                    } catch (IOException colorEx) {
                        log.warn("[charts] distribution tactic {} slice recolor skipped (non-fatal): {}",
                            n, colorEx.getMessage());
                    }
                }
                slideChartSwapper.replaceChartOnSlide(
                    slides, req.presentationId(), oldObjectId, copiedId, transforms.get(oldObjectId));
            } catch (IOException ex) {
                errors.add(chartErrors.describeChartError("Distribution Tactic " + n, ex));
            }
        }
        return errors;
    }

    private void renderTacticChart(
            Drive drive,
            Sheets sheets,
            Slides slides,
            String presentationId,
            String templateId,
            String oldObjectId,
            String copyName,
            String folderId,
            Pivot pivot,
            Map<String, SlideChartSwapper.ElementTransform> transforms,
            String tag,
            List<String> errors) throws IOException {
        if (templateId == null) {
            errors.add(tag + ": no chart-template spreadsheet id configured");
            return;
        }
        if (oldObjectId == null) {
            errors.add(tag + ": no slide chart object id configured");
            return;
        }
        String copiedId = driveCopier.copyFile(drive, templateId, copyName, folderId);
        ChartSpec spec = chartSpecBuilder.readChartSpec(sheets, templateId);
        String tab = chartSpecBuilder.findDataTab(sheets, copiedId);
        chartSheetWriter.writePivot(sheets, copiedId, tab, pivot);
        if (spec != null) {
            try {
                boolean withRate = pivot.hasClicks() || pivot.hasCompletions();
                chartSpecBuilder.injectComboSeries(
                    spec, chartSheetWriter.sheetIdForTab(sheets, copiedId, tab), withRate);
                chartSpecBuilder.applyChartSpec(sheets, copiedId, spec);
            } catch (IOException ex) {
                log.warn("[charts] {}: chart spec re-apply failed, placing chart anyway — {}",
                    tag, ex.getMessage());
            }
        }
        slideChartSwapper.replaceChartOnSlide(
            slides, presentationId, oldObjectId, copiedId, transforms.get(oldObjectId));
    }

    private Map<Integer, List<String>> tacticLineItems(List<LineItemMapping> mapping) {
        Map<Integer, List<String>> out = new LinkedHashMap<>();
        if (mapping == null) {
            return out;
        }
        for (LineItemMapping m : mapping) {
            int n = m.tacticNum() == null ? 0 : m.tacticNum();
            String liId = m.lineItemId() == null ? "" : m.lineItemId().trim();
            if (n > 0 && !liId.isEmpty()) {
                out.computeIfAbsent(n, k -> new ArrayList<>()).add(liId);
            }
        }
        return out;
    }
}
