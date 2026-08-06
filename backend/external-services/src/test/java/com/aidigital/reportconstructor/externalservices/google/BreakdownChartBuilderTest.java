package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartSlice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BreakdownChartBuilderTest {

	private BreakdownChartBuilder newBuilder() {
		// The pure methods under test use only the ChartSheetWriter (for label normalisation); the Google
		// collaborators are not touched, so nulls are safe here.
		return new BreakdownChartBuilder(
				null, new ChartSheetWriter(), null, null, null, new BreakdownChartCatalog(),
				new BreakdownSlideNaming(), null);
	}

	@Test
	void parseChartId_treatsBlankOrNonNumericAsUnconfiguredTest() {
		// Given: the builder
		BreakdownChartBuilder builder = newBuilder();

		// When-Then: only a clean integer parses; the empty default and junk are treated as unset
		assertThat(builder.parseChartId("1087145314")).isEqualTo(1087145314);
		assertThat(builder.parseChartId(" 42 ")).isEqualTo(42);
		assertThat(builder.parseChartId("")).isNull();
		assertThat(builder.parseChartId(null)).isNull();
		assertThat(builder.parseChartId("not-a-number")).isNull();
	}

	@Test
	void impressionsByLabel_dropsNonPositiveAndNormalisesLabelsTest() {
		// Given: slices for four devices, one with zero impressions and one CTV row
		BreakdownChartBuilder builder = newBuilder();
		List<BreakdownChartSlice> slices = List.of(
				new BreakdownChartSlice("Mobile", 1000.4),
				new BreakdownChartSlice("CTV", 500.0),
				new BreakdownChartSlice("Desktop", 0.0),
				new BreakdownChartSlice("Tablet", -5.0));

		// When:
		Map<String, Long> byLabel = builder.impressionsByLabel(slices);

		// Then: only positive slices survive, rounded, keyed by the normalised label (CTV → connectedtv)
		assertThat(byLabel).containsOnly(
				Map.entry("mobile", 1000L),
				Map.entry("connectedtv", 500L));
	}

	@Test
	void impressionsByLabel_emptyWhenNoPositiveSliceTest() {
		// Given: every slice is non-positive
		BreakdownChartBuilder builder = newBuilder();
		List<BreakdownChartSlice> slices = List.of(
				new BreakdownChartSlice("Mobile", 0.0),
				new BreakdownChartSlice("Desktop", -1.0));

		// When-Then: nothing to chart
		assertThat(builder.impressionsByLabel(slices)).isEmpty();
	}
}
