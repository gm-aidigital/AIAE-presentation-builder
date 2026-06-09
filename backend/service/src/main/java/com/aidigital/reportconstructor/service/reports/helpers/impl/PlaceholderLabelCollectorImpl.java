package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderLabelCollector;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring bean implementation of {@link PlaceholderLabelCollector}.
 */
@Component
@RequiredArgsConstructor
public class PlaceholderLabelCollectorImpl implements PlaceholderLabelCollector {

    @Override
    public PlaceholderResolverService.Labels collectAllLabels(GeneratePayload payload) {
        return new PlaceholderResolverService.Labels(
            collectLabelChips(payload.sheetRows()),
            collectLabelChips(payload.adjRows())
        );
    }

    List<PlaceholderResolverService.LabelChip> collectLabelChips(List<List<String>> rows) {
        List<PlaceholderResolverService.LabelChip> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String label = row.get(0) == null ? "" : row.get(0).trim();
            if (label.isEmpty()) {
                continue;
            }
            if (row.size() < 2 || row.get(1) == null) {
                continue;
            }
            String value = row.get(1).trim();
            if (value.isEmpty()) {
                continue;
            }
            out.add(new PlaceholderResolverService.LabelChip(label, value));
        }
        return out;
    }
}
