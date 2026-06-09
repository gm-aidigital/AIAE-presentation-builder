package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderValueFlattenerImplTest {

    private final PlaceholderValueFlattenerImpl flattener = new PlaceholderValueFlattenerImpl();

    @Test
    void shouldSubstituteDashForEmptyValuesTest() {
        List<PreviewSection> sections = List.of(
            new PreviewSection("Test", List.of(
                new Placeholder("{{filled}}", "Label", "value", "sheet"),
                new Placeholder("{{empty}}", "Label", "", "not_found"),
                new Placeholder("{{null}}", "Label", null, "not_found")
            ))
        );

        Map<String, String> flat = flattener.buildFlatReplacements(sections);

        assertThat(flat.get("{{filled}}")).isEqualTo("value");
        assertThat(flat.get("{{empty}}")).isEqualTo("\u2014");
        assertThat(flat.get("{{null}}")).isEqualTo("\u2014");
    }
}
