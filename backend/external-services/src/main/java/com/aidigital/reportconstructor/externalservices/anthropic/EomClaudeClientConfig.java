package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.diagnostics.ClaudeFailureLog;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the second Claude client — the one whose prompts are written for end-of-month runs.
 *
 * <p>It is the same {@link RealClaudeClient} implementation as the primary bean, wired to
 * {@link EomPromptBuilder} instead of {@link ClaudeBatchPromptBuilder}. Everything the client itself
 * does — chunking, the accept/retry contract, compression, token accounting — is shared, so the two
 * flavours can only diverge in prompt text, which is the whole point of the split.
 */
@Configuration
@ConditionalOnExpression("'${external.anthropic.api-key:}' != ''")
public class EomClaudeClientConfig {

	/** Bean name the flavour resolver qualifies on to find the end-of-month client. */
	public static final String EOM_CLAUDE_CLIENT = "eomClaudeClient";

	/**
	 * Builds the end-of-month Claude client.
	 *
	 * @param messagesClient     shared Anthropic Messages API transport
	 * @param promptBuilder      the end-of-month prompt text
	 * @param normalizer         shared reply normaliser
	 * @param compressionService shared over-limit copy shrinker
	 * @param claudeDefaults     shared empty-DTO factory used on every failure path
	 * @param geoFilter          shared workbook geo-row filter
	 * @param tokenEstimator     shared prompt-size estimator
	 * @param failureLog         shared run-scoped sink for rejected replies
	 * @param anthropicProperties shared chunking/retry configuration
	 * @return the client end-of-month runs send their prompts through
	 */
	@Bean(EOM_CLAUDE_CLIENT)
	public ClaudeClient eomClaudeClient(
			AnthropicMessagesClient messagesClient,
			EomPromptBuilder promptBuilder,
			ClaudeResponseNormalizer normalizer,
			ClaudeCompressionService compressionService,
			ReportClaudeDefaults claudeDefaults,
			WorkbookGeoFilter geoFilter,
			PromptTokenEstimator tokenEstimator,
			ClaudeFailureLog failureLog,
			AnthropicProperties anthropicProperties) {
		return new RealClaudeClient(
				messagesClient, promptBuilder, normalizer, compressionService, claudeDefaults,
				geoFilter, tokenEstimator, failureLog, anthropicProperties);
	}
}
