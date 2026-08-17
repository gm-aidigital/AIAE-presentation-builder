package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartSlice;
import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.SheetsChart;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
	void valuesByLabel_shouldDropNonPositiveAndNormaliseMatchedLabelsTest() {
		// Given: slices for four devices, one with zero impressions and one CTV row — a series whose labels
		// are matched against the workbook's own category column
		BreakdownChartBuilder builder = newBuilder();
		List<BreakdownChartSlice> slices = List.of(
				new BreakdownChartSlice("Mobile", 1000.4),
				new BreakdownChartSlice("CTV", 500.0),
				new BreakdownChartSlice("Desktop", 0.0),
				new BreakdownChartSlice("Tablet", -5.0));

		// When:
		Map<String, Double> byLabel = builder.valuesByLabel(slices, false);

		// Then: only positive slices survive, keyed by the normalised label (CTV → connectedtv)
		assertThat(byLabel).containsOnly(
				Map.entry("mobile", 1000.4),
				Map.entry("connectedtv", 500.0));

		// And: the impressions writer gets them rounded
		assertThat(builder.rounded(byLabel)).containsOnly(
				Map.entry("mobile", 1000L),
				Map.entry("connectedtv", 500L));
	}

	@Test
	void valuesByLabel_shouldKeepLabelsVerbatimWhenTheyAreWrittenIntoTheWorkbookTest() {
		// Given: audience segments, whose names go onto the chart as the reader sees them
		BreakdownChartBuilder builder = newBuilder();
		List<BreakdownChartSlice> slices = List.of(
				new BreakdownChartSlice("  In-Market: Luxury Travel ", 142.0),
				new BreakdownChartSlice("Affinity: Foodies", 118.5),
				new BreakdownChartSlice("", 100.0),
				new BreakdownChartSlice("Affinity: Sports", 0.0));

		// When:
		Map<String, Double> byLabel = builder.valuesByLabel(slices, true);

		// Then: trimmed but not normalised, in sheet order, and the blank label and the zero are dropped
		assertThat(byLabel).containsExactly(
				Map.entry("In-Market: Luxury Travel", 142.0),
				Map.entry("Affinity: Foodies", 118.5));
	}

	@Test
	void valuesByLabel_shouldBeEmptyWhenNoSliceIsPositiveTest() {
		// Given: every slice is non-positive
		BreakdownChartBuilder builder = newBuilder();
		List<BreakdownChartSlice> slices = List.of(
				new BreakdownChartSlice("Mobile", 0.0),
				new BreakdownChartSlice("Desktop", -1.0));

		// When-Then: nothing to chart
		assertThat(builder.valuesByLabel(slices, false)).isEmpty();
	}

	@Test
	void loadBreakdownChartElements_shouldKeyEveryChartOnABreakdownSlideByItsSourceWorkbookTest() {
		// Given: a deck whose audience copy carries two linked charts and whose device copy carries one; a
		// tactic slide and a chartless breakdown slide are also present
		BreakdownChartBuilder builder = newBuilder();

		// When:
		Map<String, Map<String, ChartElementRef>> bySlide = builder.chartsBySlideOf(List.of(
				chartSlide("bd_aud_2", Map.of("book_age", "el_age", "book_seg", "el_seg")),
				chartSlide("bd_dev_2", Map.of("book_dev", "el_dev")),
				chartSlide("tct_2", Map.of("book_daily", "el_daily")),
				new Page().setObjectId("bd_geo_2")));

		// Then: both audience charts are reachable, each by the workbook it links to — which is what lets the
		// age series and the segment series each replace their own chart
		assertThat(bySlide.get("bd_aud_2").keySet()).containsExactlyInAnyOrder("book_age", "book_seg");
		assertThat(bySlide.get("bd_aud_2").get("book_seg").objectId()).isEqualTo("el_seg");
		assertThat(bySlide.get("bd_dev_2").get("book_dev").objectId()).isEqualTo("el_dev");

		// And: non-breakdown slides and chartless breakdown slides are absent
		assertThat(bySlide).doesNotContainKeys("tct_2", "bd_geo_2");
	}

	private Page chartSlide(String slideId, Map<String, String> elementIdBySourceBook) {
		List<PageElement> elements = new ArrayList<>();
		elementIdBySourceBook.forEach((book, elementId) -> elements.add(new PageElement()
				.setObjectId(elementId)
				.setSize(new Size())
				.setTransform(new AffineTransform())
				.setSheetsChart(new SheetsChart().setSpreadsheetId(book).setChartId(1))));
		return new Page().setObjectId(slideId).setPageElements(elements);
	}
}
