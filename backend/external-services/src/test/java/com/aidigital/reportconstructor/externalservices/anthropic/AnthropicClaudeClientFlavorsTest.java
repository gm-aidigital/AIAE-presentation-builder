package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.enums.ReportFlavor;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicClaudeClientFlavorsTest {

	private final ClaudeClient defaultClient = mock(ClaudeClient.class);

	private final ClaudeClient eomClient = mock(ClaudeClient.class);

	@SuppressWarnings("unchecked")
	private ObjectProvider<ClaudeClient> provider(ClaudeClient client) {
		ObjectProvider<ClaudeClient> provider = mock(ObjectProvider.class);
		if (client == null) {
			when(provider.getIfAvailable(any(Supplier.class)))
					.thenAnswer(call -> ((Supplier<ClaudeClient>) call.getArgument(0)).get());
		} else {
			when(provider.getIfAvailable(any(Supplier.class))).thenReturn(client);
		}
		return provider;
	}

	@Test
	void shouldSendEomRunsThroughTheEomPromptsTest() {
		// Given:
		AnthropicClaudeClientFlavors flavors =
				new AnthropicClaudeClientFlavors(defaultClient, provider(eomClient));

		// Then: the code is matched case-insensitively, the way it arrives on the request
		assertThat(flavors.forReportType("EOM")).isSameAs(eomClient);
		assertThat(flavors.forReportType(" eom ")).isSameAs(eomClient);
		assertThat(flavors.forFlavor(ReportFlavor.EOM)).isSameAs(eomClient);
	}

	@Test
	void shouldLeaveEveryOtherReportTypeOnTheEndOfCampaignPromptsTest() {
		// Given:
		AnthropicClaudeClientFlavors flavors =
				new AnthropicClaudeClientFlavors(defaultClient, provider(eomClient));

		// Then: a typo or a report type added later ships the wording the deck has always used, never
		// mid-flight copy about a campaign that has finished
		assertThat(flavors.forReportType("EOC")).isSameAs(defaultClient);
		assertThat(flavors.forReportType("standard")).isSameAs(defaultClient);
		assertThat(flavors.forReportType(null)).isSameAs(defaultClient);
		assertThat(flavors.forReportType("  ")).isSameAs(defaultClient);
		assertThat(flavors.forFlavor(ReportFlavor.EOC)).isSameAs(defaultClient);
	}

	@Test
	void shouldFallBackToTheDefaultClientWhenNoApiKeyRegisteredTheEomOneTest() {
		// Given: a keyless deployment, where only the stub client exists
		AnthropicClaudeClientFlavors flavors =
				new AnthropicClaudeClientFlavors(defaultClient, provider(null));

		// Then: an EOM run still generates, exactly as it did before the split
		assertThat(flavors.forReportType("EOM")).isSameAs(defaultClient);
	}
}
