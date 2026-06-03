package com.aidigital.reportconstructor.service.reports.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.ReportGenerationService;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import com.aidigital.reportconstructor.service.reports.engine.TacticUtils;
import com.aidigital.reportconstructor.service.reports.ports.ChartProvider;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.usagelogging.LogUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationServiceImpl implements ReportGenerationService {

    private final ReportJobRepository jobs;
    private final PlaceholderResolverService placeholders;
    private final ClaudeClient claude;
    private final SlidesProvider slides;
    private final ChartProvider charts;
    private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ReportGenerationService> self;

    private static final Pattern PRESENTATION_ID = Pattern.compile("/d/([a-zA-Z0-9_-]+)");

    @Override
    @LogUsage
    public ReportJobEntity start(String userId, String clerkUserId, GeneratePayload payload) {
        if (payload.brief() == null || payload.brief().isBlank()) {
            throw new AppException(ErrorReason.C002, "Brief is required");
        }
        ReportJobEntity job = enqueue(userId, payload);
        self.getObject().run(job.getId(), payload, clerkUserId);
        return job;
    }

    @Override
    @Transactional
    @LogUsage
    public ReportJobEntity enqueue(String userId, GeneratePayload payload) {
        ReportJobEntity job = new ReportJobEntity();
        job.setOwnerUserId(userId);
        job.setStatus("queued");
        job.setStep(0);
        job.setTotal(7);
        job.setLabel("Queued…");
        job.setReportTypeCode(payload.reportType());
        OffsetDateTime now = OffsetDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return jobs.save(job);
    }

    @Override
    @Async
    @LogUsage
    public void run(Long jobId, GeneratePayload payload, String clerkUserId) {
        log.info("[report] starting job {}", jobId);
        try {
            advance(jobId, 1, "Reading sheet data");
            CampaignData data = placeholders.collectData(payload);
            String brief = payload.brief();

            advance(jobId, 2, "Resolving placeholders");

            boolean live = claude.isLive();

            advance(jobId, 3, "Claude — campaign batch (A)");
            ClaudeStrategic ccA = (live && placeholders.needStrategic(payload))
                ? claude.batchStrategic(data, brief) : ClaudeStrategic.empty();

            advance(jobId, 4, "Claude — tactics batch (B)");
            ClaudeTactical ccB = (live && placeholders.needTactical(payload, data))
                ? claude.batchTactical(data, brief) : ClaudeTactical.empty();

            advance(jobId, 5, "Claude — executive batch (C)");
            ClaudeResults ccC = (live && placeholders.needResults(payload, data))
                ? claude.batchResults(data, brief) : ClaudeResults.empty();

            String geoSummary = (live && placeholders.needGeoSummary(payload))
                ? claude.summarizeGeo(payload.geoRows()) : null;

            advance(jobId, 6, "Building slide deck");
            Map<String, String> all =
                placeholders.buildFlatReplacements(payload, data, ccA, ccB, ccC, geoSummary);
            UserGoogleTokenProvider clerk = userGoogleTokens.getIfAvailable();
            String userGoogleToken = clerk == null ? null : clerk.googleAccessToken(clerkUserId);
            String slideUrl = slides.createDeck(String.valueOf(jobId), all, userGoogleToken);

            trimUnusedTactics(slideUrl, payload, userGoogleToken);

            advance(jobId, 7, "Building charts");
            List<String> chartWarnings = buildCharts(slideUrl, payload, data, all, userGoogleToken);

            ReportJobEntity job = mustLoad(jobId);
            job.setStatus("done");
            job.setStep(7);
            job.setLabel("Done!");
            job.setSlideUrl(slideUrl);
            job.setWarningsJson(toWarningsJson(chartWarnings));
            job.setUpdatedAt(OffsetDateTime.now());
            jobs.save(job);
            log.info("[report] job {} done → {} ({} chart warning(s))", jobId, slideUrl, chartWarnings.size());
        } catch (Exception ex) {
            log.error("[report] job {} failed", jobId, ex);
            jobs.findById(jobId).ifPresent(job -> {
                job.setStatus("error");
                job.setErrorMessage(ex.getMessage());
                job.setUpdatedAt(OffsetDateTime.now());
                jobs.save(job);
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    @LogUsage
    public ProgressView progress(String userId, Long jobId) {
        ReportJobEntity job = jobs.findById(jobId)
            .filter(j -> userId != null && userId.equals(j.getOwnerUserId()))
            .orElseThrow(() -> new AppException(ErrorReason.C001, "Unknown job " + jobId));
        return new ProgressView(
            job.getStep(),
            job.getTotal(),
            job.getLabel() == null ? "" : job.getLabel(),
            job.getStatus(),
            job.getSlideUrl() == null ? "" : job.getSlideUrl(),
            job.getErrorMessage() == null ? "" : job.getErrorMessage(),
            parseWarnings(job.getWarningsJson())
        );
    }

    private List<String> parseWarnings(String warningsJson) {
        if (warningsJson == null || warningsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(warningsJson, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            log.warn("[report] could not parse warnings json: {}", ex.getMessage());
            return List.of();
        }
    }

    public void advance(Long jobId, int step, String label) {
        ReportJobEntity job = mustLoad(jobId);
        job.setStatus("running");
        job.setStep(step);
        job.setLabel(label);
        job.setUpdatedAt(OffsetDateTime.now());
        jobs.save(job);
    }

    private ReportJobEntity mustLoad(Long jobId) {
        return jobs.findById(jobId).orElseThrow(() ->
            new IllegalStateException("Report job not found: " + jobId));
    }

    private List<String> buildCharts(String slideUrl, GeneratePayload payload, CampaignData data,
                                     Map<String, String> all, String userGoogleToken) {
        if (payload.bqSheetId() == null || payload.bqSheetId().isBlank()
            || payload.adjRows() == null || payload.adjRows().isEmpty()
            || payload.lineItemMapping() == null || payload.lineItemMapping().isEmpty()) {
            log.info("[charts] skipped — bqSheetId/adjRows/lineItemMapping not all present");
            return List.of();
        }
        String presentationId = extractPresentationId(slideUrl);
        if (presentationId == null) {
            return List.of("Charts skipped — could not determine presentation id from " + slideUrl);
        }

        int tacticCount = Math.clamp(TacticUtils.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);
        String campaignTitle = firstNonBlank(all.get("{{Campaign_name}}"), all.get("{{client_name}}"), "Campaign");

        Map<Integer, String> distNames = new LinkedHashMap<>();
        Map<Integer, Double> distImps = new LinkedHashMap<>();
        for (int n = 1; n <= tacticCount; n++) {
            distNames.put(n, firstNonBlank(all.get("{{tactic " + n + "}}"), "Tactic " + n));
            distImps.put(n, parseNum(all.get("{{tactic " + n + " imps}}")));
        }
        double totalImps = parseNum(all.get("{{total imps}}"));

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

    private void trimUnusedTactics(String slideUrl, GeneratePayload payload, String userGoogleToken) {
        String presentationId = extractPresentationId(slideUrl);
        if (presentationId == null) {
            return;
        }
        int tacticCount = Math.clamp(TacticUtils.countTacticsInMediaPlan(payload.sheetRows()), 1, 7);
        try {
            slides.trimTactics(presentationId, tacticCount, userGoogleToken);
        } catch (RuntimeException ex) {
            log.warn("[slides] trimTactics failed for {} (non-fatal): {}", presentationId, ex.getMessage());
        }
    }

    private static String extractPresentationId(String slideUrl) {
        if (slideUrl == null) {
            return null;
        }
        Matcher m = PRESENTATION_ID.matcher(slideUrl);
        return m.find() ? m.group(1) : null;
    }

    private String toWarningsJson(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JsonProcessingException ex) {
            log.warn("[report] could not serialise warnings: {}", ex.getMessage());
            return null;
        }
    }

    private static double parseNum(String raw) {
        if (raw == null) {
            return 0.0;
        }
        String s = raw.replace(",", "").replaceAll("[^0-9.]", "");
        Matcher m = Pattern.compile("^[0-9]*\\.?[0-9]+").matcher(s);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"—".equals(v.trim())) {
                return v.trim();
            }
        }
        return "";
    }
}
