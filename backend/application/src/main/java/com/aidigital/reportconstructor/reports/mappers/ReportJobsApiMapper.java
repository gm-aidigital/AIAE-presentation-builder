package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.ReportJobCreatedV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps report-job entities and progress views to their V1 API DTOs.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface ReportJobsApiMapper {

    /**
     * Converts a persisted report job into the "created" V1 response.
     *
     * @param job the persisted report job entity
     * @return the V1 job-created DTO
     */
    @Mapping(target = "jobId", source = "id")
    ReportJobCreatedV1 toCreated(ReportJobEntity job);

    /**
     * Converts a progress view into the V1 job DTO for the given job id.
     *
     * @param jobId the report job id
     * @param view  the service progress view
     * @return the V1 report job DTO
     */
    @Mapping(target = "jobId", source = "jobId")
    @Mapping(target = "status", expression = "java(toStatus(view.status()))")
    ReportJobV1 toJob(Long jobId, ProgressView view);

    /**
     * Maps a status string to the V1 {@link ReportJobStatusV1} enum.
     *
     * @param status the service status string (may be {@code null})
     * @return the matching status enum, or {@code null} when {@code status} is {@code null}
     */
    default ReportJobStatusV1 toStatus(String status) {
        return status == null ? null : ReportJobStatusV1.fromValue(status);
    }
}
