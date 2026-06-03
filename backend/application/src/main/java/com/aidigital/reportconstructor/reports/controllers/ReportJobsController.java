package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.ReportJobsApi;
import com.aidigital.reportconstructor.api.v1.model.GenerateRequestV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobCreatedV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobV1;
import com.aidigital.reportconstructor.reports.mappers.PlaceholdersApiMapper;
import com.aidigital.reportconstructor.reports.mappers.ReportJobsApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.reports.ReportGenerationService;
import com.aidigital.reportconstructor.usagelogging.LogUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportJobsController implements ReportJobsApi {

    private final ReportGenerationService reportGeneration;
    private final ReportJobsApiMapper mapper;
    private final PlaceholdersApiMapper payloadMapper;
    private final AppUserFactory appUserFactory;

    @Override
    @LogUsage(action = "report-jobs.create")
    public ResponseEntity<ReportJobCreatedV1> createReportJob(GenerateRequestV1 body) {
        var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
        var job = reportGeneration.start(user.userId(), user.userId(), payloadMapper.toPayload(body));
        return new ResponseEntity<>(mapper.toCreated(job), HttpStatus.ACCEPTED);
    }

    @Override
    public ResponseEntity<ReportJobV1> getReportJob(Long jobId) {
        var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
        var view = reportGeneration.progress(user.userId(), jobId);
        return ResponseEntity.ok(mapper.toJob(jobId, view));
    }
}
