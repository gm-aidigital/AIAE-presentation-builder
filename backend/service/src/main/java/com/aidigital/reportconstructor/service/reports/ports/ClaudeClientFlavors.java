package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.enums.ReportFlavor;

/**
 * Picks the {@link ClaudeClient} whose prompts are written for a given report type.
 *
 * <p>The clients differ only in the prompt text they send: both are the same implementation wired to a
 * different prompt builder, so retries, chunking, parsing and usage accounting behave identically no
 * matter which one a run picks. A deployment without an Anthropic API key has one stub client and every
 * flavour resolves to it.
 */
public interface ClaudeClientFlavors {

	/**
	 * Resolves the client for a raw report type code as it arrives on the generate request.
	 *
	 * @param reportTypeCode the report type code (e.g. {@code "EOC"}, {@code "EOM"}); unknown, blank and
	 *                       {@code null} values fall back to {@link ReportFlavor#EOC}
	 * @return the client for that report type, never {@code null}
	 */
	ClaudeClient forReportType(String reportTypeCode);

	/**
	 * Resolves the client for an already-typed flavour.
	 *
	 * @param flavor the report flavour; {@code null} is treated as {@link ReportFlavor#EOC}
	 * @return the client for that flavour, never {@code null}
	 */
	ClaudeClient forFlavor(ReportFlavor flavor);
}
