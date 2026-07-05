package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps service-layer {@link ReportSummary} rows to their V1 API DTOs for the
 * "My reports" history screen.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface ReportsApiMapper {

	/**
	 * Converts a service history row into its V1 DTO.
	 *
	 * @param summary the service history row
	 * @return the V1 report summary
	 */
	@Mapping(target = "status", expression = "java(toStatus(summary.status()))")
	ReportSummaryV1 toSummary(ReportSummary summary);

	/**
	 * Converts a list of service history rows into V1 DTOs.
	 *
	 * @param summaries the service history rows
	 * @return the V1 report summaries
	 */
	List<ReportSummaryV1> toSummaries(List<ReportSummary> summaries);

	/**
	 * Maps a status wire code to the V1 {@link ReportJobStatusV1} enum.
	 *
	 * @param status the service status string (may be {@code null})
	 * @return the matching status enum, or {@code null} when {@code status} is {@code null}
	 */
	default ReportJobStatusV1 toStatus(String status) {
		return status == null ? null : ReportJobStatusV1.fromValue(status);
	}
}
