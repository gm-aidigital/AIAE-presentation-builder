package com.aidigital.reportconstructor.service.reports;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;

/**
 * Business orchestration for report generation — port of PHP {@code worker.php}.
 */
public interface ReportGenerationService {

    ReportJobEntity start(String userId, String clerkUserId, GeneratePayload payload);

    ReportJobEntity enqueue(String userId, GeneratePayload payload);

    void run(Long jobId, GeneratePayload payload, String clerkUserId);

    ProgressView progress(String userId, Long jobId);
}
