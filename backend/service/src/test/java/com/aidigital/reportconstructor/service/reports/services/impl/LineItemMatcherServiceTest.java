package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.engine.TacticCatalog;
import com.aidigital.reportconstructor.service.reports.helpers.MediaPlanTacticExtractor;
import com.aidigital.reportconstructor.service.reports.helpers.impl.LineItemNamingHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.MediaPlanTacticExtractorImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.SheetRowHelperImpl;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import com.aidigital.reportconstructor.service.reports.usage.impl.ClaudeUsageTrackerImpl;
import com.aidigital.reportconstructor.service.reports.usage.impl.NoOpClaudeUsageEventService;
import com.aidigital.reportconstructor.service.reports.services.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LineItemMatcherServiceTest {

	/**
	 * Builds a tracker whose usage events go nowhere, so these tests stay pure unit tests.
	 *
	 * @return a tracker backed by a no-op event sink
	 */
	ClaudeUsageTrackerImpl tracker() {
		return new ClaudeUsageTrackerImpl(new NoOpClaudeUsageEventService());
	}

	private final MediaPlanTacticExtractor extractor =
			new MediaPlanTacticExtractorImpl(new TacticCatalog(), new SheetRowHelperImpl());
	private final LineItemMatcherServiceImpl matcher =
			new LineItemMatcherServiceImpl(new LineItemNamingHelperImpl(), (tactics, options) -> Map.of(), extractor, tracker());

	@Test
	void extractLineItemId_readsIndexEight() {
		assertThat(matcher.extractLineItemId("a_b_c_d_e_f_g_h_42_tail")).isEqualTo("42");
		assertThat(matcher.extractLineItemId("short_name")).isNull();
	}

	@Test
	void match_autoWhenChannelHasExactlyOneId() {
		List<List<String>> bq = List.of(
				List.of("Level 1 Naming", "Channel", "Tactic"),
				List.of("x_x_x_x_x_x_x_x_99_y", "Display", "t1"),
				List.of("x_x_x_x_x_x_x_x_88_y", "Display", "t2")
		);
		List<List<String>> plan = List.of(
				List.of("Media"),
				List.of("Programmatic Display")
		);
		MatchResult result = matcher.match(bq, plan, null);
		assertThat(result.tactics()).hasSize(1);
		assertThat(result.tactics().getFirst().confidence()).isEqualTo("none");
		assertThat(result.uniqueIds()).containsExactly("88", "99");
	}

	@Test
	void match_usesAiAssistantWhenChannelHasMultipleIds() {
		// Given: one channel (Display) with two line items and two same-named tactics — the unique-ID
		// rule cannot disambiguate, so the AI assistant is consulted
		List<List<String>> bq = List.of(
				List.of("Level 1 Naming", "Channel", "Tactic"),
				List.of("a_b_c_d_e_f_g_h_616641_Contextual", "Display", "Prospecting"),
				List.of("a_b_c_d_e_f_g_h_616642_WhiteList", "Display", "Prospecting")
		);
		List<List<String>> plan = List.of(
				List.of("Media", "Comments", "Targeting"),
				List.of("Programmatic Display", "Even-paced", "3P, Contextual"),
				List.of("Programmatic Display", "Whitelist Strategy", "Curated List")
		);
		LineItemMatchAssistant assistant = (tactics, options) -> Map.of(1, "616641", 2, "616642");
		var aiMatcher = new LineItemMatcherServiceImpl(new LineItemNamingHelperImpl(), assistant, extractor, tracker());

		// When
		MatchResult result = aiMatcher.match(bq, plan, null);

		// Then: both tactics receive their AI-assigned id, marked auto-matched
		assertThat(result.tactics()).hasSize(2);
		assertThat(result.tactics().get(0).lineItemId()).isEqualTo("616641");
		assertThat(result.tactics().get(0).confidence()).isEqualTo("auto");
		assertThat(result.tactics().get(1).lineItemId()).isEqualTo("616642");
		assertThat(result.tactics().get(1).confidence()).isEqualTo("auto");
	}

	@Test
	void match_rejectsAiAssignmentWithUnknownOrCrossChannelId() {
		// Given: an ambiguous Display channel, but the assistant returns one bogus id and one valid id
		List<List<String>> bq = List.of(
				List.of("Level 1 Naming", "Channel", "Tactic"),
				List.of("a_b_c_d_e_f_g_h_616641_Contextual", "Display", "Prospecting"),
				List.of("a_b_c_d_e_f_g_h_616642_WhiteList", "Display", "Prospecting")
		);
		List<List<String>> plan = List.of(
				List.of("Media", "Comments"),
				List.of("Programmatic Display", "Even-paced"),
				List.of("Programmatic Display", "Whitelist Strategy")
		);
		LineItemMatchAssistant assistant = (tactics, options) -> Map.of(1, "999", 2, "616642");
		var aiMatcher = new LineItemMatcherServiceImpl(new LineItemNamingHelperImpl(), assistant, extractor, tracker());

		// When
		MatchResult result = aiMatcher.match(bq, plan, null);

		// Then: the unknown id is dropped, the valid one is applied
		assertThat(result.tactics().get(0).lineItemId()).isEmpty();
		assertThat(result.tactics().get(0).confidence()).isEqualTo("none");
		assertThat(result.tactics().get(1).lineItemId()).isEqualTo("616642");
		assertThat(result.tactics().get(1).confidence()).isEqualTo("auto");
	}
}
