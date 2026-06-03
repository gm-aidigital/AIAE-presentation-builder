package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.IdNamingV1;
import com.aidigital.reportconstructor.api.v1.model.LineItemMatchResultV1;
import com.aidigital.reportconstructor.api.v1.model.MappingEntryV1;
import com.aidigital.reportconstructor.service.reports.LineItemMatcherService.LineItemMeta;
import com.aidigital.reportconstructor.service.reports.LineItemMatcherService.MatchResult;
import com.aidigital.reportconstructor.service.reports.LineItemMatcherService.TacticSuggestion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LineItemMatchResultMapper {

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
