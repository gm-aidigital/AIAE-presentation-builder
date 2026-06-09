package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.IdNamingV1;
import com.aidigital.reportconstructor.api.v1.model.LineItemMatchResultV1;
import com.aidigital.reportconstructor.api.v1.model.MappingEntryV1;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService.LineItemMeta;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService.MatchResult;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService.TacticSuggestion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the V1 line-item match result DTO from the service match result, indexing
 * line-item metadata by id and grouping ids by channel for the UI.
 */
@Component
public class LineItemMatchResultMapper {

    /**
     * Converts a service match result into its V1 DTO: per-tactic mapping entries, the
     * id&rarr;naming index, channel&rarr;ids grouping, and the auto-match summary.
     *
     * @param result the service match result
     * @return the V1 line-item match result DTO
     */
    public LineItemMatchResultV1 toResult(MatchResult result) {
        Map<String, IdNamingV1> idNamings = new LinkedHashMap<>();
        Map<String, List<String>> channelToIds = new LinkedHashMap<>();
        for (LineItemMeta meta : result.lineItems()) {
            idNamings.put(meta.id(), new IdNamingV1(meta.naming(), meta.channel(), meta.tactic()));
            if (meta.channel() != null && !meta.channel().isBlank()) {
                channelToIds.computeIfAbsent(meta.channel(), k -> new ArrayList<>()).add(meta.id());
            }
        }

        List<MappingEntryV1> mapping = new ArrayList<>();
        int autoCount = 0;
        int idx = 1;
        for (TacticSuggestion s : result.tactics()) {
            boolean auto = "auto".equalsIgnoreCase(s.confidence());
            if (auto) {
                autoCount++;
            }
            String namingSample = null;
            if (s.lineItemId() != null && !s.lineItemId().isBlank()) {
                IdNamingV1 meta = idNamings.get(s.lineItemId());
                namingSample = meta == null ? null : meta.getNaming();
            }
            MappingEntryV1 entry = new MappingEntryV1(idx, s.tactic(), auto);
            entry.setLineItemId(s.lineItemId() == null || s.lineItemId().isBlank() ? null : s.lineItemId());
            entry.setNamingSample(namingSample);
            mapping.add(entry);
            idx++;
        }

        LineItemMatchResultV1 dto = new LineItemMatchResultV1(
            mapping,
            result.uniqueIds(),
            idNamings,
            channelToIds,
            List.of(),
            autoCount > 0,
            autoCount
        );
        return dto;
    }
}
