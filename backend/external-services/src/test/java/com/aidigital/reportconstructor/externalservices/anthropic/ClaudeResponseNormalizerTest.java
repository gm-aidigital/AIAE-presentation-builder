package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClaudeResponseNormalizerTest {

    private final ClaudeResponseNormalizer normalizer = new ClaudeResponseNormalizer();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void normalizeProposal_truncatesAtSentenceWhenPastHalfLimit() {
        String longText = "A. " + "x".repeat(500);
        String out = normalizer.normalizeProposal(longText, 400);
        assertEquals("A.", out);
    }

    @Test
    void normalizeC_shortTextUnchanged() {
        assertEquals("short overview.", normalizer.normalizeC("short overview.", 400));
    }

    @Test
    void normalizeThoughts_splitsPipeIntoFour() {
        List<String> slots = normalizer.normalizeThoughts("one | two | three | four");
        assertEquals("one", slots.get(0));
        assertEquals("two", slots.get(1));
        assertEquals("three", slots.get(2));
        assertEquals("four", slots.get(3));
    }

    @Test
    void extractText_concatenatesContentBlocks() throws Exception {
        var resp = json.readTree("""
            {"content":[{"type":"text","text":"hello "},{"type":"text","text":"world"}]}
            """);
        assertEquals("hello world", normalizer.extractText(resp));
    }

    @Test
    void limitStrategicPoint_max22Chars() {
        assertThat(normalizer.limitStrategicPoint("abcdefghijklmnopqrstuvwxyz")).hasSize(22);
    }

    @Test
    void limitAudienceSegments_max80AtComma() {
        String longSeg =
                "one, two, three, four, five, six, seven, eight, nine, ten, eleven, twelve, thirteen, fourteen, fifteen, sixteen";
        assertThat(longSeg.length()).isGreaterThan(80);
        String out = normalizer.limitAudienceSegments(longSeg);
        assertThat(out).isNotNull();
        assertThat(out.length()).isLessThanOrEqualTo(80);
        assertThat(out).doesNotContain("thirteen");
        assertThat(out).endsWith("twelve");
    }

    @Test
    void normalizeThoughts_splitsIntoExactlyFour() {
        List<String> slots = normalizer.normalizeThoughts("a | b | c | d | e");
        assertThat(slots).hasSize(4);
        assertThat(slots.get(3)).isEqualTo("d");
    }

    @Test
    void limitGeoSummary_max40() {
        String out = normalizer.limitGeoSummary("New York, Los Angeles, Chicago, Houston, Phoenix");
        assertThat(out).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void limitResultsOverview_uses380Budget() {
        String longText = "x".repeat(500);
        String out = normalizer.limitResultsOverview(longText);
        assertThat(out).isNotNull();
        assertThat(out.length()).isLessThanOrEqualTo(380);
    }

    @Test
    void limitTacticOverview_uses210Budget() {
        String out = normalizer.limitTacticOverview("word ".repeat(80));
        assertThat(out).isNotNull();
        assertThat(out.length()).isLessThanOrEqualTo(210);
    }
}
