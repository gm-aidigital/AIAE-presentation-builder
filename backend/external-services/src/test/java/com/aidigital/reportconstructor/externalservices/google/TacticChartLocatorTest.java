package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.SheetsChart;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TacticChartLocatorTest {

	@Test
	void chartsBySource_shouldIndexEachLinkedChartByTheWorkbookItPointsAtTest() {
		// Given: a duplicated tactic slide carrying the three linked placeholder charts plus a plain shape
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming());
		Page slide = new Page().setObjectId("tct_3").setPageElements(List.of(
				chartElement("el_daily", "book_daily", 100.0),
				chartElement("el_monthly", "book_monthly", 200.0),
				chartElement("el_dist", "book_dist", 300.0),
				new PageElement().setObjectId("el_text")));

		// When:
		Map<String, ChartElementRef> bySource = locator.chartsBySource(slide);

		// Then: every chart is reachable by its source workbook id — the only key both the master and the
		// chart step know — and its geometry is captured so the replacement lands in the same spot
		assertThat(bySource.keySet()).containsExactly("book_daily", "book_monthly", "book_dist");
		assertThat(bySource.get("book_monthly").objectId()).isEqualTo("el_monthly");
		assertThat(bySource.get("book_monthly").transform().slideId()).isEqualTo("tct_3");
		assertThat(bySource.get("book_monthly").transform().transform().getTranslateX()).isEqualTo(200.0);
	}

	@Test
	void chartsBySource_shouldKeepTheFirstOfTwoChartsLinkedToTheSameWorkbookTest() {
		// Given: a slide whose two charts point at the same workbook — an ambiguous master, not a crash
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming());
		Page slide = new Page().setObjectId("tct_1").setPageElements(List.of(
				chartElement("el_first", "book_shared", 10.0),
				chartElement("el_second", "book_shared", 20.0)));

		// When-Then: the first one wins, so the pass still renders one chart instead of failing the tactic
		assertThat(locator.chartsBySource(slide).get("book_shared").objectId()).isEqualTo("el_first");
	}

	@Test
	void tacticNumberOf_shouldRecognizeOnlyActiveTacticSlideIdsTest() {
		// Given: a locator and a deck naming convention of tct_<n>
		TacticChartLocator locator = new TacticChartLocator(new BreakdownSlideNaming());

		// When-Then: a copy within the active range resolves; a breakdown copy, a template slide, a
		// tactic above the active count and a null id do not
		assertThat(locator.tacticNumberOf("tct_2", 3)).isEqualTo(2);
		assertThat(locator.tacticNumberOf("tct_4", 3)).isNull();
		assertThat(locator.tacticNumberOf("bd_dev_2", 3)).isNull();
		assertThat(locator.tacticNumberOf("p7", 3)).isNull();
		assertThat(locator.tacticNumberOf(null, 3)).isNull();
	}

	private PageElement chartElement(String objectId, String spreadsheetId, double translateX) {
		return new PageElement()
				.setObjectId(objectId)
				.setSize(new Size())
				.setTransform(new AffineTransform().setTranslateX(translateX))
				.setSheetsChart(new SheetsChart().setSpreadsheetId(spreadsheetId).setChartId(1));
	}
}
