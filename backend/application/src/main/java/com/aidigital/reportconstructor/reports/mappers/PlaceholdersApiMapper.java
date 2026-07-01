package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.DateRangeResultV1;
import com.aidigital.reportconstructor.api.v1.model.GenerateRequestV1;
import com.aidigital.reportconstructor.api.v1.model.LabelPairV1;
import com.aidigital.reportconstructor.api.v1.model.PlaceholderEntryV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewRequestV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewResultV1;
import com.aidigital.reportconstructor.api.v1.model.SectionV1;
import com.aidigital.reportconstructor.api.v1.model.SourceV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.services.LabelChip;
import com.aidigital.reportconstructor.service.reports.services.Labels;
import com.aidigital.reportconstructor.service.reports.services.PreviewResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;
import java.util.Map;

/**
 * Maps placeholder preview/generation service records to their V1 API DTOs and
 * converts inbound request DTOs into service-layer payloads.
 */
@Mapper(config = ApplicationMapperConfig.class, nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface PlaceholdersApiMapper {

	/**
	 * Converts a generate request into the service generation payload.
	 *
	 * @param body the generate request DTO
	 * @return the service generation payload
	 */
	@Mapping(target = "reportType", expression = "java(body.getReportType().getValue())")
	GeneratePayload toPayload(GenerateRequestV1 body);

	/**
	 * Converts a preview request into the service generation payload (no BigQuery sheet id).
	 *
	 * @param body the preview request DTO
	 * @return the service generation payload
	 */
	@Mapping(target = "reportType", expression = "java(body.getReportType().getValue())")
	@Mapping(target = "bqSheetId", ignore = true)
	GeneratePayload toPayload(PreviewRequestV1 body);

	/**
	 * Converts the detected raw-data flight window into its V1 date-range response. A {@code null}
	 * window yields an empty response with {@code null} start/end (no dated rows found).
	 *
	 * @param range the detected inclusive date window, or {@code null} when none was found
	 * @return the V1 date-range response DTO
	 */
	DateRangeResultV1 toDateRangeResponse(FlightDates range);

	/**
	 * Converts an API mapping entry into a service line-item mapping.
	 *
	 * @param entry the API mapping entry DTO
	 * @return the service line-item mapping
	 */
	@Mapping(target = "tactic", source = "tacticName")
	LineItemMapping toLineItemMapping(
			com.aidigital.reportconstructor.api.v1.model.MappingEntryV1 entry);

	/**
	 * Converts a preview result into its V1 API response, including grouped label chips and stats.
	 *
	 * @param result the service preview result
	 * @return the V1 preview response DTO
	 */
	@Mapping(target = "allLabels", expression = "java(toAllLabels(result.labels()))")
	@Mapping(target = "stats.found", source = "found")
	@Mapping(target = "stats.total", source = "total")
	PreviewResultV1 toPreviewResponse(PreviewResult result);

	/**
	 * Converts one preview section into its V1 DTO.
	 *
	 * @param section the service preview section
	 * @return the V1 section DTO
	 */
	SectionV1 toSection(PreviewSection section);

	/**
	 * Converts a list of preview sections into their V1 DTOs.
	 *
	 * @param sections the service preview sections
	 * @return the V1 section DTOs
	 */
	List<SectionV1> toSections(List<PreviewSection> sections);

	/**
	 * Converts one resolved placeholder into its V1 DTO, mapping the source tag.
	 *
	 * @param placeholder the resolved placeholder
	 * @return the V1 placeholder entry DTO
	 */
	@Mapping(target = "source", expression = "java(toSource(placeholder.source()))")
	PlaceholderEntryV1 toPlaceholder(Placeholder placeholder);

	/**
	 * Converts a list of resolved placeholders into their V1 DTOs.
	 *
	 * @param placeholders the resolved placeholders
	 * @return the V1 placeholder entry DTOs
	 */
	List<PlaceholderEntryV1> toPlaceholders(List<Placeholder> placeholders);

	/**
	 * Groups the label chips into the {@code sheet} / {@code adj} buckets the UI renders.
	 *
	 * @param labels the service label chips (may be {@code null})
	 * @return a map of bucket name to its label pairs; empty when {@code labels} is {@code null}
	 */
	default Map<String, List<LabelPairV1>> toAllLabels(Labels labels) {
		if (labels == null) {
			return Map.of();
		}
		return Map.of(
				"sheet", toLabelPairs(labels.sheet()),
				"adj", toLabelPairs(labels.adj())
		);
	}

	/**
	 * Converts a list of label chips into their V1 DTOs.
	 *
	 * @param chips the service label chips
	 * @return the V1 label pair DTOs
	 */
	List<LabelPairV1> toLabelPairs(List<LabelChip> chips);

	/**
	 * Converts one label chip into its V1 DTO.
	 *
	 * @param chip the service label chip
	 * @return the V1 label pair DTO
	 */
	LabelPairV1 toLabelPair(LabelChip chip);

	/**
	 * Maps a service source tag to the V1 {@link SourceV1} enum, normalising legacy aliases.
	 *
	 * @param source the service source tag (may be {@code null})
	 * @return the matching {@link SourceV1}, or {@code null} when {@code source} is {@code null}
	 */
	default SourceV1 toSource(String source) {
		if (source == null) {
			return null;
		}
		return switch (source) {
			case "ai" -> SourceV1.CLAUDE;
			case "fallback" -> SourceV1.UI;
			default -> SourceV1.fromValue(source);
		};
	}
}
