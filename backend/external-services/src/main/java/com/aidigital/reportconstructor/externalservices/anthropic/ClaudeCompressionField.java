package com.aidigital.reportconstructor.externalservices.anthropic;

/**
 * One oversized placeholder field submitted to Claude Batch D for meaning-preserving compression.
 *
 * @param key      identifier echoed back as the JSON key in the compression response
 * @param text     the raw, uncompressed text to shrink
 * @param maxChars the character budget {@code text} must fit within after compression
 */
public record ClaudeCompressionField(String key, String text, int maxChars) {

}
