package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.enums.ReportFlavor;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClientFlavors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Resolves a run's report type to the Claude client carrying that report type's prompts.
 *
 * <p>The default client is the primary one — {@link RealClaudeClient} wired to the end-of-campaign
 * prompts, or {@link StubClaudeClient} when no API key is configured. The end-of-month client is
 * optional for exactly that reason: it only exists alongside a live API key, and without it every
 * flavour falls back to the default, which keeps a keyless deployment behaving as it always did.
 */
@Component
public class AnthropicClaudeClientFlavors implements ClaudeClientFlavors {

	/** The primary client: end-of-campaign prompts when live, the stub otherwise. */
	private final ClaudeClient defaultClient;

	/** The end-of-month client; absent when no API key is configured. */
	private final ObjectProvider<ClaudeClient> eomClient;

	public AnthropicClaudeClientFlavors(
			ClaudeClient defaultClient,
			@Qualifier(EomClaudeClientConfig.EOM_CLAUDE_CLIENT) ObjectProvider<ClaudeClient> eomClient) {
		this.defaultClient = defaultClient;
		this.eomClient = eomClient;
	}

	@Override
	public ClaudeClient forReportType(String reportTypeCode) {
		return forFlavor(flavorOf(reportTypeCode));
	}

	@Override
	public ClaudeClient forFlavor(ReportFlavor flavor) {
		if (flavor != ReportFlavor.EOM) {
			return defaultClient;
		}
		return eomClient.getIfAvailable(() -> defaultClient);
	}

	/**
	 * Maps a raw report type code onto a flavour, defaulting to {@link ReportFlavor#EOC}.
	 *
	 * <p>Anything that is not recognisably an end-of-month code resolves to end-of-campaign on purpose: a
	 * typo or a report type added later then ships the wording the deck has always used rather than
	 * mid-flight copy about a campaign that has finished.
	 *
	 * @param reportTypeCode the report type code from the generate request, possibly {@code null} or blank
	 * @return the matching flavour, or {@link ReportFlavor#EOC} when nothing matches
	 */
	ReportFlavor flavorOf(String reportTypeCode) {
		if (reportTypeCode == null || reportTypeCode.isBlank()) {
			return ReportFlavor.EOC;
		}
		String code = reportTypeCode.trim().toUpperCase(Locale.ROOT);
		for (ReportFlavor flavor : ReportFlavor.values()) {
			if (flavor.getCode().equals(code)) {
				return flavor;
			}
		}
		return ReportFlavor.EOC;
	}
}
