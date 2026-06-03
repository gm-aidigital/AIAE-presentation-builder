package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.GenerateRequestV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewRequestV1;
import com.aidigital.reportconstructor.api.v1.model.LabelPairV1;
import com.aidigital.reportconstructor.api.v1.model.PlaceholderEntryV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewResultV1;
import com.aidigital.reportconstructor.api.v1.model.SectionV1;
import com.aidigital.reportconstructor.api.v1.model.SourceV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.reports.PlaceholderResolverService.LabelChip;
import com.aidigital.reportconstructor.service.reports.PlaceholderResolverService.Labels;
import com.aidigital.reportconstructor.service.reports.PlaceholderResolverService.PreviewResult;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;
import java.util.Map;
@Mapper(config = ApplicationMapperConfig.class, nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface PlaceholdersApiMapper {

    @Mapping(target = "reportType", expression = "java(body.getReportType().getValue())")
    GeneratePayload toPayload(GenerateRequestV1 body);

    @Mapping(target = "reportType", expression = "java(body.getReportType().getValue())")
    @Mapping(target = "bqSheetId", ignore = true)
    GeneratePayload toPayload(PreviewRequestV1 body);

    @Mapping(target = "tactic", source = "tacticName")
    GeneratePayload.LineItemMapping toLineItemMapping(
        com.aidigital.reportconstructor.api.v1.model.MappingEntryV1 entry);

    @Mapping(target = "allLabels", expression = "java(toAllLabels(result.labels()))")
    @Mapping(target = "stats.found", source = "found")
    @Mapping(target = "stats.total", source = "total")
    PreviewResultV1 toPreviewResponse(PreviewResult result);

    SectionV1 toSection(PreviewSection section);

    List<SectionV1> toSections(List<PreviewSection> sections);

    @Mapping(target = "source", expression = "java(toSource(placeholder.source()))")
    PlaceholderEntryV1 toPlaceholder(Placeholder placeholder);

    List<PlaceholderEntryV1> toPlaceholders(List<Placeholder> placeholders);

    default Map<String, List<LabelPairV1>> toAllLabels(Labels labels) {
        if (labels == null) {
            return Map.of();
        }
        return Map.of(
            "sheet", toLabelPairs(labels.sheet()),
            "adj", toLabelPairs(labels.adj())
        );
    }

    List<LabelPairV1> toLabelPairs(List<LabelChip> chips);

    LabelPairV1 toLabelPair(LabelChip chip);

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
