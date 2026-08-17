package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.SoWhatPhrase;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoWhatResolverTest {

	@Mock
	CampaignResolvers campaignResolvers;
	@Mock
	TacticExtractionHelper tacticExtraction;

	@InjectMocks
	SoWhatResolver resolver;

	@Test
	void everyCataloguePhrase_shouldFitTheColumnAndReadAsAnAchievementTest() {
		// Given-When-Then: the column fits 45 characters, so a longer phrase would wrap out of its row —
		// and the whole point of a fixed catalogue is that this cannot happen at runtime
		for (SoWhatPhrase phrase : SoWhatPhrase.values()) {
			assertThat(phrase.text()).isNotBlank();
			assertThat(phrase.text().length()).isLessThanOrEqualTo(SoWhatPhrase.MAX_LENGTH);
			assertThat(phrase.goalKeywords()).isNotEmpty();
		}
	}

	@Test
	void resolveSoWhat_shouldPreferAHandTypedPhraseOverTheCatalogueTest() {
		// Given: the user typed their own phrase into the Adjustments grid
		when(campaignResolvers.resolve(List.of(), List.of(), "So what 3:"))
				.thenReturn(new Resolved("So what 3:", "Locked in the launch-week share of voice", "adj"));

		// When:
		Resolved resolved = resolver.resolveSoWhat(3, "Display", List.of(), List.of(),
				new Resolved("Tactic 3 goal:", "Awareness", "sheet"));

		// Then: the manual value wins untouched — the sheet is the source of truth once the user edited it
		assertThat(resolved.value()).isEqualTo("Locked in the launch-week share of voice");
		assertThat(resolved.source()).isEqualTo("adj");
	}

	@Test
	void resolveSoWhat_shouldPickThePhraseTheTacticsFunnelGoalImpliesTest() {
		// Given: no manual value, and a conversion-goal tactic
		when(campaignResolvers.resolve(List.of(), List.of(), "So what 1:"))
				.thenReturn(new Resolved("So what 1:", null, "not_found"));

		// When:
		Resolved resolved = resolver.resolveSoWhat(1, "Display", List.of(), List.of(),
				new Resolved("Tactic 1 goal:", "Conversion", "sheet"));

		// Then: the down-funnel phrase, tagged as auto-derived so the preview shows where it came from
		assertThat(resolved.value()).isEqualTo(SoWhatPhrase.CONVERSION.text());
		assertThat(resolved.source()).isEqualTo("auto");
		assertThat(resolved.label()).contains("funnel goal");
	}

	@Test
	void phraseFor_shouldMatchGoalWordingCaseAndDecorationInsensitivelyTest() {
		// Given-When-Then: goals as they are actually written in a media plan
		assertThat(resolver.phraseFor("awareness / reach", "Display")).isEqualTo(SoWhatPhrase.AWARENESS_REACH);
		assertThat(resolver.phraseFor("upper funnel", "Display")).isEqualTo(SoWhatPhrase.AWARENESS_REACH);
		assertThat(resolver.phraseFor("consideration", "Display")).isEqualTo(SoWhatPhrase.CONSIDERATION);
		assertThat(resolver.phraseFor("site traffic", "Display")).isEqualTo(SoWhatPhrase.ENGAGEMENT);
		assertThat(resolver.phraseFor("lead generation", "Display")).isEqualTo(SoWhatPhrase.CONVERSION);
		assertThat(resolver.phraseFor("retargeting", "Display")).isEqualTo(SoWhatPhrase.RETENTION);

		// And: a goal naming two stages resolves to the upper-funnel one, the stage such a line is bought against
		assertThat(resolver.phraseFor("awareness & conversion", "Display")).isEqualTo(SoWhatPhrase.AWARENESS_REACH);
	}

	@Test
	void phraseFor_shouldFallBackToWhatTheChannelsKpiSaysWhenTheGoalIsUnusableTest() {
		// Given: tactics whose goal column is empty or says nothing about the funnel
		when(tacticExtraction.getTacticKpiType("CTV")).thenReturn("vcr");
		when(tacticExtraction.getTacticKpiType("Display")).thenReturn("ctr");
		when(tacticExtraction.getTacticKpiType("Sponsorship")).thenReturn("");

		// When-Then: a completion-rate channel claims attention, a click channel engagement, and an unmapped
		// channel the reach play — never a down-funnel claim the plan never promised
		assertThat(resolver.phraseFor("", "CTV")).isEqualTo(SoWhatPhrase.AWARENESS_ATTENTION);
		assertThat(resolver.phraseFor("", "Display")).isEqualTo(SoWhatPhrase.ENGAGEMENT);
		assertThat(resolver.phraseFor("tbd", "Sponsorship")).isEqualTo(SoWhatPhrase.AWARENESS_REACH);
	}
}
