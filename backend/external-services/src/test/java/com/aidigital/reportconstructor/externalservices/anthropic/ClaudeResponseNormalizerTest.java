package com.aidigital.reportconstructor.externalservices.anthropic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
	void limitFOpportunity_capsAt180Chars() {
		String out = normalizer.limitFOpportunity("word " + "x".repeat(300) + ".");
		assertThat(out.length()).isLessThanOrEqualTo(180);
	}

	@Test
	void limitFFact_capsAt140Chars() {
		String out = normalizer.limitFFact("word " + "x".repeat(300) + ".");
		assertThat(out.length()).isLessThanOrEqualTo(140);
	}

	@Test
	void limitFStorytelling_capsAt320Chars() {
		String out = normalizer.limitFStorytelling("word " + "x".repeat(500) + ".");
		assertThat(out.length()).isLessThanOrEqualTo(320);
	}

	@Test
	void limitFFact_shortTextUnchanged() {
		assertEquals("Actual frequency was 3.16, aligned with our plan.",
				normalizer.limitFFact("Actual frequency was 3.16, aligned with our plan."));
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
	void limitRecommendationTitle_max30CharsAtWordBoundary() {
		String out = normalizer.limitRecommendationTitle("Scale Connected TV In Evening Dayparts Significantly");
		assertThat(out.length()).isLessThanOrEqualTo(30);
		assertThat(out).doesNotEndWith(" ");
		assertThat(out).startsWith("Scale Connected TV");
	}

	@Test
	void limitRecommendationTitle_nullBecomesEmpty() {
		assertThat(normalizer.limitRecommendationTitle(null)).isEmpty();
	}

	@Test
	void limitRecommendationText_max130CharsEndsOnSentence() {
		String longText = "x".repeat(200);
		String out = normalizer.limitRecommendationText(longText);
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(130);
	}

	@Test
	void limitRecommendationText_shortTextUnchanged() {
		assertEquals("Reallocate spend to CTV.", normalizer.limitRecommendationText("Reallocate spend to CTV."));
	}

	@Test
	void limitAudienceSegments_max80AtComma() {
		String longSeg =
				"one, two, three, four, five, six, seven, eight, nine, ten, eleven, twelve, thirteen, fourteen, " +
						"fifteen, sixteen";
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
	void shouldStripQuotesAndTrailingPeriodFromPrimaryKpisTest() {
		// Given: the model wrapped the KPI line in quotes and added a trailing period
		String out = normalizer.limitPrimaryKpis("\"Imps, CTR, VCR, R&F\".");

		// Then: the quotes and trailing period are removed, leaving a clean single line
		assertThat(out).isEqualTo("Imps, CTR, VCR, R&F");
	}

	@Test
	void shouldReturnNullForBlankPrimaryKpisTest() {
		// When-Then:
		assertThat(normalizer.limitPrimaryKpis("   ")).isNull();
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

	@Test
	void shouldFallBackToLastWordBoundaryWhenNoPunctuationFoundTest() {
		// Given: words separated by spaces, no '.'/',' anywhere, longer than the 210-char budget
		String longText = "alpha ".repeat(60);

		// When:
		String out = normalizer.normalizeC(longText, 210);

		// Then: cut lands on a space boundary (never mid-word) and is finished with a period
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(210);
		assertThat(out).doesNotEndWith("alph.");
		assertThat(out).endsWith("alpha.");
		String body = out.substring(0, out.length() - 1);
		assertThat(longText.substring(0, body.length() + 1)).startsWith(body + " ");
	}

	@Test
	void shouldFallBackToLastWordBoundaryForStrategicOverviewTest() {
		// Given: a strategic overview with no sentence-ending punctuation, longer than the 240-char budget
		String longOverview = "tactic ".repeat(40);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: cut lands on a space boundary (never mid-word) and is finished with a period
		assertThat(out.length()).isLessThanOrEqualTo(240);
		assertThat(out).doesNotEndWith("tacti.");
		assertThat(out).endsWith("tactic.");
		String body = out.substring(0, out.length() - 1);
		assertThat(longOverview.substring(0, body.length() + 1)).startsWith(body + " ");
	}

	@Test
	void shouldPreferSentenceEndingPeriodOverALaterCommaTest() {
		// Given: a period sits just past the 75% threshold, and a comma sits further along but still
		// inside the cut window — picking the later (comma) would end on an unfinished clause
		String longText = "a".repeat(160) + "." + "b".repeat(20) + "," + "c".repeat(50);

		// When:
		String out = normalizer.normalizeC(longText, 210);

		// Then: cuts at the period, not the later comma, so the result is a finished sentence
		assertThat(out).isNotNull();
		assertThat(out).endsWith(".");
		assertThat(out).doesNotContain(",");
	}

	@Test
	void shouldPreferSentenceEndingPeriodOverALaterCommaForStrategicOverviewTest() {
		// Given: a period sits just past position 180, and a comma sits further along but still inside
		// the 240-char cut window
		String longOverview = "a".repeat(190) + "." + "b".repeat(20) + "," + "c".repeat(50);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: cuts at the period, not the later comma
		assertThat(out).endsWith(".");
		assertThat(out).doesNotContain(",");
	}

	@Test
	void shouldNotTreatADecimalPointAsASentenceEndingPeriodTest() {
		// Given: the only "." in the cut window is a decimal point inside a number (e.g. "94.72"),
		// not a real sentence end, followed by plain text with no further punctuation
		String longOverview = "Programmatic delivered a 94.72 percent rate " + "across the board ".repeat(15);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: the decimal point is not mistaken for a sentence boundary
		assertThat(out).doesNotEndWith("94.");
		assertThat(out.length()).isLessThanOrEqualTo(240);
	}

	@Test
	void shouldStripDanglingTrailingCommaWhenFallingBackToWordBoundaryTest() {
		// Given: no sentence-ending period anywhere, but the word-boundary cut lands right after a comma
		// in a list (e.g. "... buyers, shoppers,")
		String longOverview = "Layered intent segments " + "buyers, shoppers, decision makers, ".repeat(8);

		// When:
		String out = normalizer.limitStrategicOverview(longOverview);

		// Then: the dangling comma is removed instead of left as the visible ending
		assertThat(out).doesNotEndWith(",");
		assertThat(out.length()).isLessThanOrEqualTo(240);
	}

	@Test
	void shouldDropTrailingPrepositionAndAddPeriodWhenFinishingSentenceTest() {
		// Given: a fragment left by a word-boundary cut that ends on a dangling preposition
		String fragment = "validating video as the primary engagement driver for";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then: the dangling word is dropped and the result reads as a finished sentence
		assertEquals("validating video as the primary engagement driver.", out);
	}

	@Test
	void shouldDropTrailingVerbAndAddPeriodWhenFinishingSentenceTest() {
		// Given: a fragment that ends on a dangling auxiliary verb
		String fragment = "matched audience intent and the segment strategy is";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then:
		assertEquals("matched audience intent and the segment strategy.", out);
	}

	@Test
	void shouldAddPeriodWhenTailIsAContentWordTest() {
		// Given: a fragment whose last word carries meaning, just missing terminal punctuation
		String fragment = "concentrated delivery within the trade zones";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then: nothing is dropped, only a period is appended
		assertEquals("concentrated delivery within the trade zones.", out);
	}

	@Test
	void shouldLeaveAlreadyCompleteSentenceUnchangedWhenFinishingTest() {
		// Given: a fragment that already ends on sentence-ending punctuation
		String fragment = "the campaign delivered qualified local reach efficiently.";

		// When:
		String out = normalizer.finishSentence(fragment);

		// Then:
		assertEquals("the campaign delivered qualified local reach efficiently.", out);
	}

	@Test
	void shouldEndOnCompleteSentenceAfterWordBoundaryFallbackInNormalizeCTest() {
		// Given: prose longer than the 210-char budget, no sentence-ending period anywhere, engineered so
		// the word-boundary cut lands on a dangling connector word
		String longText = ("the team kept spending across many channels and "
				+ "validating the approach with ").repeat(6);

		// When:
		String out = normalizer.normalizeC(longText, 210);

		// Then: the result fits and reads as a finished thought rather than ending mid-clause
		assertThat(out).isNotNull();
		assertThat(out.length()).isLessThanOrEqualTo(211);
		assertThat(out).endsWith(".");
		assertThat(out).doesNotEndWith(" with.");
		assertThat(out).doesNotEndWith(" and.");
		assertThat(out).doesNotEndWith(" the.");
	}
}
