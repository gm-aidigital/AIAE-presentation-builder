package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.model.AffineTransform;
import com.google.api.services.slides.v1.model.Size;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TacticChartBuilderTest {

	private TacticChartBuilder newBuilder(ChartTemplateCatalog templates) {
		return new TacticChartBuilder(
				Mockito.mock(com.aidigital.reportconstructor.service.reports.engine.ChartPivot.class),
				Mockito.mock(ChartSheetWriter.class),
				Mockito.mock(ChartSpecBuilder.class),
				Mockito.mock(SlideChartSwapper.class),
				Mockito.mock(DriveCopier.class),
				Mockito.mock(ChartErrorTranslator.class),
				templates,
				Mockito.mock(TacticLineItemGrouper.class),
				Mockito.mock(ChartFileSharer.class),
				new TacticChartLocator(new BreakdownSlideNaming()));
	}

	@Test
	void resolveTarget_shouldUseTheScannedChartOnTheDuplicatedTacticSlideTest() {
		// Given: a master-model deck whose tactic 2 copy was scanned, and a catalog with one workbook per type
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(555);
		templates.setDailyTemplateSheetId("book_daily");
		TacticChartBuilder builder = newBuilder(templates);
		ElementTransform xform = new ElementTransform(new Size(), new AffineTransform(), "tct_2");
		Map<Integer, Map<String, ChartElementRef>> scanned =
				Map.of(2, Map.of("book_daily", new ChartElementRef("el_daily_2", xform)));

		// When: resolving the daily target for tactic 2
		ChartTarget target = builder.resolveTarget(scanned, Map.of(), "legacy_daily_2", 2, "book_daily", 555);

		// Then: the scanned element wins over the configured legacy id, and brings its captured geometry
		assertThat(target.objectId()).isEqualTo("el_daily_2");
		assertThat(target.transform()).isSameAs(xform);
		assertThat(target.chartIdInSheet()).isEqualTo(555);
	}

	@Test
	void resolveTarget_shouldReturnNullWhenTheCopyCarriesNoChartLinkedToThatWorkbookTest() {
		// Given: a scanned tactic slide whose charts point at other workbooks
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setDistTemplateSheetId("book_dist");
		TacticChartBuilder builder = newBuilder(templates);
		Map<Integer, Map<String, ChartElementRef>> scanned = Map.of(
				1, Map.of("book_daily", new ChartElementRef("el_daily_1", null)));

		// When-Then: no target rather than a wrong one, so the caller reports the chart instead of
		// overwriting the pacing chart with a pie
		assertThat(builder.resolveTarget(scanned, Map.of(), "legacy_dist_1", 1, "book_dist", 1)).isNull();
	}

	@Test
	void resolveTarget_shouldFallBackToTheConfiguredSlotAndItsTransformOnALegacyDeckTest() {
		// Given: a legacy deck — nothing was scanned, so the placeholder ids come from configuration
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setChartIdInSheet(777);
		TacticChartBuilder builder = newBuilder(templates);
		ElementTransform xform = new ElementTransform(new Size(), new AffineTransform(), "p7");
		Map<String, ElementTransform> transforms = Map.of("legacy_daily_1", xform);

		// When:
		ChartTarget target = builder.resolveTarget(Map.of(), transforms, "legacy_daily_1", 1, "book_daily", 777);

		// Then: the configured id is used with its transform from the deck-wide read
		assertThat(target.objectId()).isEqualTo("legacy_daily_1");
		assertThat(target.transform()).isSameAs(xform);
		assertThat(target.chartIdInSheet()).isEqualTo(777);

		// And: an unconfigured slot resolves to nothing
		assertThat(builder.resolveTarget(Map.of(), transforms, null, 1, "book_daily", 777)).isNull();
	}

	@Test
	void templateFor_shouldPreferTheSingleMasterWorkbookOverThePerSlotMapTest() {
		// Given: a catalog carrying both the master-model workbook and the legacy per-slot ids
		ChartTemplateCatalog templates = new ChartTemplateCatalog();
		templates.setDailyTemplateSheetIds(Map.of(1, "legacy_book_1"));
		templates.setMonthlyTemplateSheetIds(Map.of(1, "legacy_monthly_1"));
		templates.setMonthlyTemplateSheetId("book_monthly");

		// When-Then: the single workbook wins where configured, the per-slot map still answers elsewhere
		assertThat(templates.monthlyTemplateFor(1)).isEqualTo("book_monthly");
		assertThat(templates.monthlyTemplateFor(28)).isEqualTo("book_monthly");
		assertThat(templates.dailyTemplateFor(1)).isEqualTo("legacy_book_1");
		assertThat(templates.dailyTemplateFor(2)).isNull();
	}
}
