package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.helpers.impl.LineItemNamingHelperImpl;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import com.aidigital.reportconstructor.service.reports.services.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LineItemMatcherServiceTest {

	private final LineItemMatcherServiceImpl matcher =
			new LineItemMatcherServiceImpl(new LineItemNamingHelperImpl(), (tactics, options) -> Map.of());

	@Test
	void extractLineItemId_readsIndexEight() {
		assertThat(matcher.extractLineItemId("a_b_c_d_e_f_g_h_42_tail")).isEqualTo("42");
		assertThat(matcher.extractLineItemId("short_name")).isNull();
	}

	@Test
	void extractTactics_skipsStopPhrasesAndNonWhitelist() {
		List<List<String>> plan = List.of(
				List.of("Media", "Comments"),
				List.of("Total media", ""),
				List.of("Programmatic Display", "note"),
				List.of("Not A Real Tactic", ""),
				List.of("Meta (CPM)", "")
		);
		assertThat(matcher.extractTactics(plan)).containsExactly("Programmatic Display", "Meta (CPM)");
	}

	@Test
	void extractTacticRows_capturesGroupLabelAndRowContext() {
		// Given: a plan with a section label above the tactic and targeting on the tactic row
		List<List<String>> plan = List.of(
				List.of("Media", "Comments", "Targeting"),
				List.of("Grapevine Vintage Railroad", "", ""),
				List.of("Google SEM", "Even-paced", "Keyword-based")
		);

		// When
		var rows = matcher.extractTacticRows(plan);

		// Then: the group label and the row's other cells are joined into the context
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst().name()).isEqualTo("Google SEM");
		assertThat(rows.getFirst().context()).contains("Grapevine Vintage Railroad", "Even-paced", "Keyword-based");
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
		MatchResult result = matcher.match(bq, plan);
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
		var aiMatcher = new LineItemMatcherServiceImpl(new LineItemNamingHelperImpl(), assistant);

		// When
		MatchResult result = aiMatcher.match(bq, plan);

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
		var aiMatcher = new LineItemMatcherServiceImpl(new LineItemNamingHelperImpl(), assistant);

		// When
		MatchResult result = aiMatcher.match(bq, plan);

		// Then: the unknown id is dropped, the valid one is applied
		assertThat(result.tactics().get(0).lineItemId()).isEmpty();
		assertThat(result.tactics().get(0).confidence()).isEqualTo("none");
		assertThat(result.tactics().get(1).lineItemId()).isEqualTo("616642");
		assertThat(result.tactics().get(1).confidence()).isEqualTo("auto");
	}
}
