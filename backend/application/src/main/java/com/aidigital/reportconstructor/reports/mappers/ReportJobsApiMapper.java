package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.ReportJobCreatedV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportJobV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.ProgressView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = ApplicationMapperConfig.class)
public interface ReportJobsApiMapper {

    @Mapping(target = "jobId", source = "id")
    ReportJobCreatedV1 toCreated(ReportJobEntity job);

    @Mapping(target = "jobId", source = "jobId")
    @Mapping(target = "status", expression = "java(toStatus(view.status()))")
    ReportJobV1 toJob(Long jobId, ProgressView view);

    default ReportJobStatusV1 toStatus(String status) {
        return status == null ? null : ReportJobStatusV1.fromValue(status);
    }
}
