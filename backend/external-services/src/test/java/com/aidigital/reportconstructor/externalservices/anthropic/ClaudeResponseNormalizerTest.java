package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
