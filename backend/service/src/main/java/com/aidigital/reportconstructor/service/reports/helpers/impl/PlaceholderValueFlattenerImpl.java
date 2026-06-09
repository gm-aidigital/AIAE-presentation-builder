package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderValueFlattener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring bean implementation of {@link PlaceholderValueFlattener}.
 */
@Component
public class PlaceholderValueFlattenerImpl implements PlaceholderValueFlattener {

    private static final String DASH = "\u2014"; // —

    @Override
    public Map<String, String> buildFlatReplacements(List<PreviewSection> sections) {
        Map<String, String> flat = new LinkedHashMap<>();
        for (PreviewSection sec : sections) {
            for (Placeholder ph : sec.placeholders()) {
                String v = ph.value();
                flat.put(ph.key(), v == null || v.isEmpty() ? DASH : v);
            }
        }
        return flat;
    }
}
