package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartJob;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartRequest;
import com.aidigital.reportconstructor.service.reports.ports.BreakdownChartSlice;
import com.google.api.services.slides.v1.Slides;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Presentation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Links the per-tactic audience/device breakdown charts onto the duplicated breakdown slides. Each
 * breakdown master slide carries an embedded chart linked to a shared source workbook; duplicating the
 * master copies that chart still pointing at the shared workbook, so every copy would render the same
 * empty data. This builder gives each copy its own data: it copies the section's source workbook, writes
 * the tactic's impressions into it (share of voice stays a live formula), and swaps the copy's chart for
 * a fresh chart linked to the new workbook.
 *
 * <p>Pure orchestration over the injected Google helper beans, mirroring {@link TacticChartBuilder}: it
 * holds no Google clients itself — those arrive per request as a {@link ChartClients} bundle — and
 * collects per-chart failures rather than aborting the rest.
 */
@Slf4j
@Component
public class BreakdownChartBuilder {

	private final DriveCopier driveCopier;
	private final ChartSheetWriter chartSheetWriter;
	private final ChartSpecBuilder chartSpecBuilder;
	private final SlideChartSwapper slideChartSwapper;
	private final ChartErrorTranslator chartErrors;
	private final BreakdownChartCatalog catalog;
	private final BreakdownSlideNaming naming;

	public BreakdownChartBuilder(
			DriveCopier driveCopier,
			ChartSheetWriter chartSheetWriter,
			ChartSpecBuilder chartSpecBuilder,
			SlideChartSwapper slideChartSwapper,
			ChartErrorTranslator chartErrors,
			BreakdownChartCatalog catalog,
			BreakdownSlideNaming naming) {
		this.driveCopier = driveCopier;
		this.chartSheetWriter = chartSheetWriter;
		this.chartSpecBuilder = chartSpecBuilder;
		this.slideChartSwapper = slideChartSwapper;
		this.chartErrors = chartErrors;
		this.catalog = catalog;
		this.naming = naming;
	}

	/**
	 * Builds and links every requested breakdown chart, returning per-chart error strings.
	 *
	 * @param clients the Drive/Sheets/Slides clients to use for this request
	 * @param req     the breakdown chart request describing the deck and per-tactic slices
	 * @return human-readable error strings for any per-chart failures (empty on full success)
	 */
	public List<String> buildBreakdownCharts(ChartClients clients, BreakdownChartRequest req) {
		List<String> errors = new ArrayList<>();
		if (req.jobs() == null || req.jobs().isEmpty()) {
			return errors;
		}
		Map<String, ChartElementRef> chartsBySlide =
				loadBreakdownChartElements(clients.slides(), req.presentationId(), errors);

		String folderId = null;
		try {
			folderId = driveCopier.createFolder(clients.drive(), "Breakdown charts — " + req.campaignTitle());
		} catch (IOException ex) {
			log.warn("[breakdown-charts] could not create folder, copies go to root: {}", ex.getMessage());
		}

		for (BreakdownChartJob job : req.jobs()) {
			try {
				renderBreakdownChart(clients, req, job, chartsBySlide, folderId, errors);
			} catch (IOException | RuntimeException ex) {
				errors.add(chartErrors.describeChartError(
						"Breakdown " + job.breakdownCode() + " tactic " + job.tacticNum(), ex));
			}
		}
		return errors;
	}

	/**
	 * Renders one breakdown chart: resolves its section config and target chart element, copies the source
	 * workbook, writes the tactic's impressions and swaps the slide chart. Missing config, an absent chart
	 * element or a job with no positive slice is recorded as an error rather than thrown.
	 *
	 * @param clients       the Google clients for this request
	 * @param req           the breakdown chart request (source of the deck id and copy names)
	 * @param job           the breakdown chart inputs
	 * @param chartsBySlide breakdown slide object id &rarr; its embedded chart element
	 * @param folderId      the Drive output folder id, or {@code null} for the root
	 * @param errors        collector for non-fatal per-chart errors
	 * @throws IOException when a Google API call fails irrecoverably
	 */
	void renderBreakdownChart(
			ChartClients clients, BreakdownChartRequest req, BreakdownChartJob job,
			Map<String, ChartElementRef> chartsBySlide, String folderId, List<String> errors) throws IOException {
		BreakdownType type = BreakdownType.fromCode(job.breakdownCode()).orElse(null);
		if (type == null) {
			return;
		}
		String tag = "Breakdown " + type.code() + " tactic " + job.tacticNum();
		String sourceSheetId = catalog.getSourceSheetIds().get(type.code());
		Integer chartIdInSheet = parseChartId(catalog.getChartIdInSheet().get(type.code()));
		if (sourceSheetId == null || sourceSheetId.isBlank() || chartIdInSheet == null) {
			errors.add(tag + ": no chart-source spreadsheet id / chart id configured");
			return;
		}
		String slideId = naming.slideId(type, job.tacticNum());
		ChartElementRef chartRef = chartsBySlide.get(slideId);
		if (chartRef == null) {
			errors.add(tag + ": no linked chart found on breakdown slide " + slideId);
			return;
		}
		Map<String, Long> impsByLabel = impressionsByLabel(job.slices());
		if (impsByLabel.isEmpty()) {
			errors.add(tag + ": no positive impressions to chart");
			return;
		}

		String copyId = driveCopier.copyFile(
				clients.drive(), sourceSheetId, tag + " — " + req.campaignTitle(), folderId);
		String tab = chartSpecBuilder.findDataTab(clients.sheets(), copyId);
		int written = chartSheetWriter.writeBreakdownImpressions(clients.sheets(), copyId, tab, impsByLabel);
		if (written == 0) {
			errors.add(tag + ": no category labels matched the chart source — chart left empty");
			return;
		}
		slideChartSwapper.replaceChartOnSlide(
				clients.slides(), req.presentationId(), chartRef.objectId(), copyId, chartRef.transform(),
				chartIdInSheet);
	}

	/**
	 * Parses a configured in-sheet chart id, treating a null, blank or non-numeric value as unconfigured so
	 * an empty default (the feature-off state) is a safe skip rather than a startup failure.
	 *
	 * @param raw the configured chart-id text
	 * @return the chart id, or {@code null} when unset or not an integer
	 */
	Integer parseChartId(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(raw.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Rounds and indexes a job's slices by normalised category label, dropping non-positive impressions so
	 * a blank or unparseable cell never zeroes the chart. A duplicate label keeps the last slice.
	 *
	 * @param slices the job's chart slices
	 * @return normalised label &rarr; rounded impressions (positive only)
	 */
	Map<String, Long> impressionsByLabel(List<BreakdownChartSlice> slices) {
		Map<String, Long> byLabel = new LinkedHashMap<>();
		if (slices == null) {
			return byLabel;
		}
		for (BreakdownChartSlice slice : slices) {
			if (slice == null || slice.impressions() <= 0) {
				continue;
			}
			byLabel.put(chartSheetWriter.normalizeBreakdownLabel(slice.label()), Math.round(slice.impressions()));
		}
		return byLabel;
	}

	/**
	 * Fetches the deck and maps every breakdown slide (object id prefixed {@code bd_}) to its embedded
	 * chart element — object id plus captured size/transform — so each chart can be replaced in place. A
	 * breakdown slide with no chart element is simply absent from the map.
	 *
	 * @param slides         the authenticated Slides client
	 * @param presentationId the deck to scan
	 * @param errors         accumulator for a read failure message
	 * @return breakdown slide object id &rarr; its chart element reference; empty when the read failed
	 */
	Map<String, ChartElementRef> loadBreakdownChartElements(
			Slides slides, String presentationId, List<String> errors) {
		Map<String, ChartElementRef> out = new LinkedHashMap<>();
		try {
			Presentation pres = slides.presentations().get(presentationId)
					.setFields("slides.objectId,"
							+ "slides.pageElements(objectId,size,transform,sheetsChart)")
					.execute();
			if (pres.getSlides() == null) {
				return out;
			}
			for (Page slide : pres.getSlides()) {
				String slideId = slide.getObjectId();
				if (slideId == null || !slideId.startsWith("bd_") || slide.getPageElements() == null) {
					continue;
				}
				for (PageElement el : slide.getPageElements()) {
					if (el.getSheetsChart() != null && el.getObjectId() != null) {
						out.put(slideId, new ChartElementRef(
								el.getObjectId(), new ElementTransform(el.getSize(), el.getTransform(), slideId)));
						break;
					}
				}
			}
		} catch (IOException ex) {
			errors.add("Breakdown charts: could not read presentation layout — " + ex.getMessage());
		}
		return out;
	}
}
