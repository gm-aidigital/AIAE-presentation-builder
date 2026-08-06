package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.ReportJobStatusV1;
import com.aidigital.reportconstructor.api.v1.model.ReportResumeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
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
	 * Converts a resumable draft into its V1 DTO, flattening the stored wizard state onto the
	 * response so the client reads one object rather than a nested one.
	 *
	 * @param resume the service-layer draft
	 * @return the V1 resume DTO
	 */
	@Mapping(target = "reportType", expression = "java(toReportType(resume.state().reportType()))")
	@Mapping(target = "brief", source = "state.brief")
	@Mapping(target = "changeLog", source = "state.changeLog")
	@Mapping(target = "marketVolume", source = "state.marketVolume")
	@Mapping(target = "dateFilter", source = "state.dateFilter")
	@Mapping(target = "estimateDaypartGender", source = "state.estimateDaypartGender")
	@Mapping(target = "breakdownSelections", source = "state.breakdownSelections")
	@Mapping(target = "tacticNames", source = "state.tacticNames")
	ReportResumeV1 toResume(ReportResume resume);

	/**
	 * Maps a report-type wire code to the V1 {@link ReportTypeV1} enum.
	 *
	 * <p>Resolved by value rather than by constant name: the spec's values are the contract, and a
	 * draft stored by an older build may carry a code this enum no longer has.
	 *
	 * @param reportType the stored report type code (may be {@code null} or unknown)
	 * @return the matching type enum, or {@code null} when there is no usable code
	 */
	default ReportTypeV1 toReportType(String reportType) {
		if (reportType == null || reportType.isBlank()) {
			return null;
		}
		try {
			return ReportTypeV1.fromValue(reportType);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

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
