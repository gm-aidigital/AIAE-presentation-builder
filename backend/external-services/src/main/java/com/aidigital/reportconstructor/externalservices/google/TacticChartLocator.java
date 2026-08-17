package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import com.google.api.services.slides.v1.model.SheetsChart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the placeholder charts on the duplicated main tactic slides. Under the master model the deck's
 * tactic slides are runtime copies, so their chart elements get fresh object ids that no configuration can
 * name — the ids have to be read back from the deck.
 *
 * <p>Each copy carries all three placeholder charts (daily pacing, monthly impressions, weighted
 * contribution), still linked to the three source workbooks the master was linked to. That link is the
 * identity used here: charts are keyed by the spreadsheet id they point at, which the chart step already
 * knows because it is about to copy that same workbook. Keying on the link rather than on element order or
 * geometry is what keeps the mapping stable when the designer moves a chart around the slide.
 *
 * <p>Mirrors {@link BreakdownChartBuilder#loadBreakdownChartElements} — the same problem for the breakdown
 * copies, which need no keying because they carry a single chart each.
 */
@Slf4j
@Component
public class TacticChartLocator {

	/** Field mask: slide order plus the chart elements' ids, geometry and source-workbook link. */
	private static final String CHART_FIELDS =
			"slides.objectId,slides.pageElements(objectId,size,transform,sheetsChart)";

	private final BreakdownSlideNaming naming;

	public TacticChartLocator(BreakdownSlideNaming naming) {
		this.naming = naming;
	}

	/**
	 * Scans a deck for the linked charts sitting on the duplicated tactic slides.
	 *
	 * <p>Returns an empty map for a deck built on the legacy 28-slot template (it carries no duplicated
	 * tactic slides), which is the caller's signal to fall back to the configured per-slot chart object ids.
	 *
	 * @param slides         the authenticated Slides client
	 * @param presentationId the deck to scan
	 * @param tacticCount    number of active tactics; slides above it are ignored
	 * @param errors         accumulator for a read-failure message
	 * @return tactic number &rarr; (source spreadsheet id &rarr; that chart's element); empty when the deck
	 *         has no duplicated tactic slides or could not be read
	 */
	public Map<Integer, Map<String, ChartElementRef>> load(
			Slides slides, String presentationId, int tacticCount, List<String> errors) {
		Map<Integer, Map<String, ChartElementRef>> byTactic = new LinkedHashMap<>();
		try {
			Presentation deck = slides.presentations().get(presentationId).setFields(CHART_FIELDS).execute();
			if (deck.getSlides() == null) {
				return byTactic;
			}
			for (Page slide : deck.getSlides()) {
				Integer tacticNum = tacticNumberOf(slide.getObjectId(), tacticCount);
				if (tacticNum == null || slide.getPageElements() == null) {
					continue;
				}
				byTactic.put(tacticNum, chartsBySource(slide));
			}
		} catch (IOException ex) {
			errors.add("Tactic charts: could not read presentation layout — " + ex.getMessage());
		}
		return byTactic;
	}

	/**
	 * Indexes one tactic slide's linked charts by the spreadsheet each one points at. A slide with two charts
	 * linked to the same workbook keeps the first — the master is expected to link its three placeholders to
	 * three distinct workbooks, and a duplicate link is ambiguous rather than fatal.
	 *
	 * @param slide the tactic slide to read
	 * @return source spreadsheet id &rarr; the chart element linked to it
	 */
	Map<String, ChartElementRef> chartsBySource(Page slide) {
		Map<String, ChartElementRef> bySource = new LinkedHashMap<>();
		for (PageElement element : slide.getPageElements()) {
			SheetsChart chart = element.getSheetsChart();
			if (chart == null || chart.getSpreadsheetId() == null || element.getObjectId() == null) {
				continue;
			}
			bySource.putIfAbsent(chart.getSpreadsheetId(), new ChartElementRef(
					element.getObjectId(),
					new ElementTransform(element.getSize(), element.getTransform(), slide.getObjectId())));
		}
		return bySource;
	}

	/**
	 * Reads the tactic number back out of a duplicated tactic slide's object id, i.e. the inverse of
	 * {@link BreakdownSlideNaming#tacticSlideId(int)}. Any other slide id — a template slide, a breakdown
	 * copy — and any number outside the active range yields {@code null}.
	 *
	 * @param slideObjectId the slide's object id (may be {@code null})
	 * @param tacticCount   number of active tactics
	 * @return the 1-based tactic number, or {@code null} when this is not an active tactic slide
	 */
	Integer tacticNumberOf(String slideObjectId, int tacticCount) {
		if (slideObjectId == null) {
			return null;
		}
		for (int n = 1; n <= tacticCount; n++) {
			if (naming.tacticSlideId(n).equals(slideObjectId)) {
				return n;
			}
		}
		return null;
	}
}
