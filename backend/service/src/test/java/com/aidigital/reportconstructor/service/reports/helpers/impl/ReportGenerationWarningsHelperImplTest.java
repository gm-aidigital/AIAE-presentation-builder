package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportGenerationWarningsHelperImplTest {

    private final ReportGenerationWarningsHelperImpl helper =
        new ReportGenerationWarningsHelperImpl(new ObjectMapper());

    @Test
    void shouldReturnEmptyListWhenWarningsJsonIsBlankTest() {
        assertThat(helper.parseWarnings(null)).isEmpty();
        assertThat(helper.parseWarnings("  ")).isEmpty();
    }

    @Test
    void shouldParseValidWarningsJsonTest() throws Exception {
        String json = new ObjectMapper().writeValueAsString(List.of("Chart A failed", "Chart B skipped"));

        assertThat(helper.parseWarnings(json))
            .containsExactly("Chart A failed", "Chart B skipped");
    }

    @Test
    void shouldReturnEmptyListWhenWarningsJsonIsMalformedTest() {
        assertThat(helper.parseWarnings("{not-json")).isEmpty();
    }

    @Test
    void shouldReturnNullWhenSerializingEmptyWarningsTest() {
        assertThat(helper.serializeWarnings(null)).isNull();
        assertThat(helper.serializeWarnings(List.of())).isNull();
    }

    @Test
    void shouldSerializeWarningsToJsonArrayTest() {
        String json = helper.serializeWarnings(List.of("Charts failed: timeout"));

        assertThat(helper.parseWarnings(json)).containsExactly("Charts failed: timeout");
    }
}
