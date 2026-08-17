package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.engine.ChartPivot;
import com.aidigital.reportconstructor.service.reports.engine.Headers;
import com.aidigital.reportconstructor.service.reports.engine.Pivot;
import com.aidigital.reportconstructor.service.reports.ports.ChartRequest;
import com.google.api.services.sheets.v4.model.ChartSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the daily, monthly and distribution charts for every active tactic and swaps
 * them onto the deck. Pure orchestration over the injected Google helper beans: it holds
 * no Google clients itself — those arrive per request as a {@link ChartClients} bundle.
 * Per-chart failures are collected and returned rather than aborting the rest of the deck.
 */
@Slf4j
@Component
public class TacticChartBuilder {

	private final ChartPivot chartPivot;
	private final ChartSheetWriter chartSheetWriter;
	private final ChartSpecBuilder chartSpecBuilder;
	private final SlideChartSwapper slideChartSwapper;
	private final DriveCopier driveCopier;
	private final ChartErrorTranslator chartErrors;
	private final ChartTemplateCatalog templates;
	private final TacticLineItemGrouper lineItemGrouper;
	private final ChartFileSharer chartFileSharer;
	private final TacticChartLocator chartLocator;

	public TacticChartBuilder(
			ChartPivot chartPivot,
			ChartSheetWriter chartSheetWriter,
			ChartSpecBuilder chartSpecBuilder,
			SlideChartSwapper slideChartSwapper,
			DriveCopier driveCopier,
			ChartErrorTranslator chartErrors,
			ChartTemplateCatalog templates,
			TacticLineItemGrouper lineItemGrouper,
			ChartFileSharer chartFileSharer,
			TacticChartLocator chartLocator) {
		this.chartPivot = chartPivot;
		this.chartSheetWriter = chartSheetWriter;
		this.chartSpecBuilder = chartSpecBuilder;
		this.slideChartSwapper = slideChartSwapper;
		this.driveCopier = driveCopier;
		this.chartErrors = chartErrors;
		this.templates = templates;
		this.lineItemGrouper = lineItemGrouper;
		this.chartFileSharer = chartFileSharer;
		this.chartLocator = chartLocator;
	}

	/**
	 * Creates the chart output folder, parses the BigQuery header row, then builds the daily,
	 * monthly and distribution charts for the requested tactics and swaps them onto the deck.
	 *
	 * @param clients the Drive/Sheets/Slides clients to use for this request
	 * @param req     the chart request describing the deck, tactic count and BigQuery rows
	 * @return human-readable error strings for any per-chart failures (empty on full success)
	 */
	public List<String> buildAllCharts(ChartClients clients, ChartRequest req) {
		List<String> errors = new ArrayList<>();

		String folderId = null;
		try {
			folderId = driveCopier.createFolder(clients.drive(), "Charts — " + req.campaignTitle());
		} catch (IOException ex) {
			log.warn("[charts] could not create chart folder, copies go to root: {}", ex.getMessage());
		}
		chartFileSharer.shareFolder(clients.drive(), folderId);

		Headers headers = chartPivot.parseBqHeaders(req.bqRows());

		// Under the master model the tactic slides are runtime copies whose chart element ids exist nowhere in
		// configuration, so they are read back from the deck once and shared by all three chart passes. An empty
		// result means a legacy deck (no duplicated tactic slides) and the configured per-slot ids are used.
		Map<Integer, Map<String, ChartElementRef>> tacticCharts =
				chartLocator.load(clients.slides(), req.presentationId(), req.tacticCount(), errors);

		errors.addAll(buildDailyCharts(clients, req, headers, folderId, tacticCharts));
		errors.addAll(buildMonthlyCharts(clients, req, headers, folderId, tacticCharts));
		errors.addAll(buildDistributionCharts(clients, req, folderId, tacticCharts));
		return errors;
	}

	/**
	 * Resolves where one tactic's chart of a given type goes on the deck: the scanned chart element on the
	 * duplicated tactic slide (master model), or the configured per-slot placeholder id plus its transform
	 * from the deck-wide transform map (legacy model).
	 *
	 * <p>In the master model the element is found by the source workbook it is linked to — the same
	 * {@code templateId} that is about to be copied for this chart — which is why the id is passed in.
	 *
	 * @param tacticCharts tactic number &rarr; (source spreadsheet id &rarr; chart element); empty for a
	 *                     legacy deck
	 * @param transforms   deck-wide element transforms, used by the legacy path only
	 * @param legacyId     the configured placeholder object id for this tactic slot (legacy path)
	 * @param tacticNum    the 1-based tactic number
	 * @param templateId   the source workbook this chart is copied from, used as the master-model key
	 * @param chartId      the embedded chart id to pull out of that workbook
	 * @return the resolved target, or {@code null} when neither model can name a placeholder to replace
	 */
	ChartTarget resolveTarget(
			Map<Integer, Map<String, ChartElementRef>> tacticCharts, Map<String, ElementTransform> transforms,
			String legacyId, int tacticNum, String templateId, int chartId) {
		if (!tacticCharts.isEmpty()) {
			ChartElementRef ref = tacticCharts.getOrDefault(tacticNum, Map.of()).get(templateId);
			return ref == null ? null : new ChartTarget(ref.objectId(), ref.transform(), chartId);
		}
		return legacyId == null ? null : new ChartTarget(legacyId, transforms.get(legacyId), chartId);
	}

	/**
	 * Builds the daily combo chart for each requested tactic.
	 *
	 * @param clients  the Google clients for this request
	 * @param req      the chart request
	 * @param headers  parsed BigQuery column indices
	 * @param folderId the Drive output folder id, or {@code null} for the root
	 * @param tacticCharts tactic number &rarr; its scanned chart elements by source workbook; empty for a
	 *                     legacy deck
	 * @return per-chart error strings (empty on full success)
	 */
	List<String> buildDailyCharts(
			ChartClients clients, ChartRequest req, Headers headers, String folderId,
			Map<Integer, Map<String, ChartElementRef>> tacticCharts) {
		List<String> errors = new ArrayList<>();
		boolean fromSheet = req.dailyPivots() != null;
		if (!fromSheet && !headers.valid()) {
			errors.add("Daily: BQ sheet — Date or Impressions column not found");
			return errors;
		}
		Map<Integer, List<String>> tacticLineItems =
				fromSheet ? Map.of() : lineItemGrouper.groupByTactic(req.lineItemMapping());
		// The scan already captured every copy's chart geometry, so the deck-wide transform read is only made
		// for a legacy deck — where the placeholder ids come from configuration and carry no geometry.
		Map<String, ElementTransform> transforms = tacticCharts.isEmpty()
				? slideChartSwapper.loadTransforms(clients.slides(), req.presentationId(), errors, "Daily")
				: Map.of();

		for (int n = 1; n <= req.tacticCount(); n++) {
			List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
			try {
				Pivot pivot = fromSheet
						? req.dailyPivots().getOrDefault(n, emptyPivot())
						: chartPivot.buildDailyPivot(req.bqRows(), liIds, headers, req.flightTs());
				if (pivot.isEmpty()) {
					errors.add("Tactic " + n + ": no pacing data"
							+ (fromSheet ? " in sheet" : " (line item ids: " + String.join(",", liIds) + ")"));
					continue;
				}
				String templateId = templates.dailyTemplateFor(n);
				renderComboChart(clients, req.presentationId(), folderId, errors,
						new ComboChartJob(
								templateId,
								resolveTarget(tacticCharts, transforms,
										templates.getDailySlideObjectIds().get(n), n, templateId,
									templates.dailyChartId()),
								"Chart Tactic " + n + " — " + req.campaignTitle(),
								pivot,
								"Tactic " + n,
								req.tacticKpiTypes() == null ? null : req.tacticKpiTypes().get(n)));
			} catch (IOException | RuntimeException ex) {
				errors.add(chartErrors.describeChartError("Tactic " + n, ex));
			}
		}
		return errors;
	}

	/**
	 * Builds the monthly combo chart for each requested tactic.
	 *
	 * @param clients  the Google clients for this request
	 * @param req      the chart request
	 * @param headers  parsed BigQuery column indices
	 * @param folderId the Drive output folder id, or {@code null} for the root
	 * @param tacticCharts tactic number &rarr; its scanned chart elements by source workbook; empty for a
	 *                     legacy deck
	 * @return per-chart error strings (empty on full success)
	 */
	List<String> buildMonthlyCharts(
			ChartClients clients, ChartRequest req, Headers headers, String folderId,
			Map<Integer, Map<String, ChartElementRef>> tacticCharts) {
		List<String> errors = new ArrayList<>();
		boolean fromSheet = req.monthlyPivots() != null;
		if (!fromSheet && !headers.valid()) {
			errors.add("Monthly: BQ sheet — Date or Impressions column not found");
			return errors;
		}
		Map<Integer, List<String>> tacticLineItems =
				fromSheet ? Map.of() : lineItemGrouper.groupByTactic(req.lineItemMapping());
		boolean multiYear = !fromSheet && chartPivot.isMultiYear(req.bqRows(), headers, req.flightTs());
		Map<String, ElementTransform> transforms = tacticCharts.isEmpty()
				? slideChartSwapper.loadTransforms(clients.slides(), req.presentationId(), errors, "Monthly")
				: Map.of();

		for (int n = 1; n <= req.tacticCount(); n++) {
			List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
			try {
				Pivot pivot = fromSheet
						? req.monthlyPivots().getOrDefault(n, emptyPivot())
						: chartPivot.buildMonthlyPivot(req.bqRows(), liIds, headers, req.flightTs(), multiYear);
				if (pivot.isEmpty()) {
					errors.add("Monthly Tactic " + n + ": no data"
							+ (fromSheet ? " in sheet" : " (line item ids: " + String.join(",", liIds) + ")"));
					continue;
				}
				String templateId = templates.monthlyTemplateFor(n);
				renderComboChart(clients, req.presentationId(), folderId, errors,
						new ComboChartJob(
								templateId,
								resolveTarget(tacticCharts, transforms,
										templates.getMonthlySlideObjectIds().get(n), n, templateId,
									templates.monthlyChartId()),
								"Monthly Chart Tactic " + n + " — " + req.campaignTitle(),
								pivot,
								"Monthly Tactic " + n,
								req.tacticKpiTypes() == null ? null : req.tacticKpiTypes().get(n)));
			} catch (IOException | RuntimeException ex) {
				errors.add(chartErrors.describeChartError("Monthly Tactic " + n, ex));
			}
		}
		return errors;
	}

	/**
	 * Builds the distribution (pie) chart for each requested tactic.
	 *
	 * @param clients  the Google clients for this request
	 * @param req      the chart request
	 * @param folderId the Drive output folder id, or {@code null} for the root
	 * @param tacticCharts tactic number &rarr; its scanned chart elements by source workbook; empty for a
	 *                     legacy deck
	 * @return per-chart error strings (empty on full success)
	 */
	List<String> buildDistributionCharts(
			ChartClients clients, ChartRequest req, String folderId,
			Map<Integer, Map<String, ChartElementRef>> tacticCharts) {
		List<String> errors = new ArrayList<>();
		Map<String, ElementTransform> transforms = tacticCharts.isEmpty()
				? slideChartSwapper.loadTransforms(clients.slides(), req.presentationId(), errors, "Distribution")
				: Map.of();

		for (int n = 1; n <= req.tacticCount(); n++) {
			String templateId = templates.distTemplateFor(n);
			if (templateId == null) {
				errors.add("Distribution Tactic " + n + ": no chart-template spreadsheet id configured");
				continue;
			}
			ChartTarget target = resolveTarget(tacticCharts, transforms,
					templates.getDistSlideObjectIds().get(n), n, templateId, templates.distChartId());
			if (target == null) {
				errors.add("Distribution Tactic " + n + ": no slide chart object id configured");
				continue;
			}
			try {
				double tacticImps = req.distTacticImps().getOrDefault(n, 0.0);
				renderDistributionChart(clients, req.presentationId(), folderId,
						new DistributionChartJob(
								n,
								templateId,
								target,
								"Distribution Chart Tactic " + n + " — " + req.campaignTitle(),
								req.distTacticNames().getOrDefault(n, "Tactic " + n),
								tacticImps,
								req.distTotalImps() - tacticImps));
			} catch (IOException | RuntimeException ex) {
				errors.add(chartErrors.describeChartError("Distribution Tactic " + n, ex));
			}
		}
		return errors;
	}

	/**
	 * Renders one combo chart: copies the template, writes the pivot, re-applies the chart
	 * spec and swaps the placeholder chart on the slide. Missing template/object ids are
	 * recorded as errors rather than thrown.
	 *
	 * @param clients        the Google clients for this request
	 * @param presentationId the deck whose placeholder chart is replaced
	 * @param folderId       the Drive output folder id, or {@code null} for the root
	 * @param errors         collector for non-fatal per-chart errors
	 * @param job            the combo chart inputs
	 * @throws IOException when a Google API call fails irrecoverably
	 */
	void renderComboChart(
			ChartClients clients,
			String presentationId,
			String folderId,
			List<String> errors,
			ComboChartJob job) throws IOException {
		if (job.templateId() == null) {
			errors.add(job.tag() + ": no chart-template spreadsheet id configured");
			return;
		}
		if (job.target() == null) {
			errors.add(job.tag() + ": no slide chart placeholder found (neither a configured object id nor a "
					+ "chart linked to " + job.templateId() + " on the tactic slide)");
			return;
		}
		String copiedId = driveCopier.copyFile(clients.drive(), job.templateId(), job.copyName(), folderId);
		chartFileSharer.shareLooseCopy(clients.drive(), folderId, copiedId);
		ChartSpec spec = chartSpecBuilder.readChartSpec(clients.sheets(), job.templateId());
		String tab = chartSpecBuilder.findDataTab(clients.sheets(), copiedId);
		chartSheetWriter.writePivot(clients.sheets(), copiedId, tab, job.pivot(), job.kpiType());
		if (spec != null) {
			try {
				boolean withRate = job.pivot().hasClicks() || job.pivot().hasCompletions();
				chartSpecBuilder.injectComboSeries(
						spec, chartSheetWriter.sheetIdForTab(clients.sheets(), copiedId, tab), withRate);
				chartSpecBuilder.applyChartSpec(clients.sheets(), copiedId, spec);
			} catch (IOException ex) {
				log.warn("[charts] {}: chart spec re-apply failed, placing chart anyway — {}",
						job.tag(), ex.getMessage());
			}
		}
		slideChartSwapper.replaceChartOnSlide(
				clients.slides(), presentationId, job.target().objectId(), copiedId,
				job.target().transform(), job.target().chartIdInSheet());
	}

	/**
	 * Renders one distribution (pie) chart: copies the template, writes the slice values,
	 * re-applies the pie colours and swaps the placeholder chart on the slide.
	 *
	 * @param clients        the Google clients for this request
	 * @param presentationId the deck whose placeholder chart is replaced
	 * @param folderId       the Drive output folder id, or {@code null} for the root
	 * @param job            the distribution chart inputs
	 * @throws IOException when a Google API call fails irrecoverably
	 */
	void renderDistributionChart(
			ChartClients clients,
			String presentationId,
			String folderId,
			DistributionChartJob job) throws IOException {
		String copiedId = driveCopier.copyFile(clients.drive(), job.templateId(), job.copyName(), folderId);
		chartFileSharer.shareLooseCopy(clients.drive(), folderId, copiedId);
		String tab = chartSpecBuilder.findDataTab(clients.sheets(), copiedId);
		chartSheetWriter.writeDistribution(
				clients.sheets(), copiedId, tab, job.tacticName(), job.tacticImp(), job.otherImps());
		// Pie slice colors are not recolored here: the Sheets API v4 pie chart spec has no per-slice color
		// field, so the copy keeps the template's own slice colors. (The old injectPieSliceColors pushed a
		// non-existent "slices" field and the API rejected the whole updateChartSpec with a 400.)
		slideChartSwapper.replaceChartOnSlide(
				clients.slides(), presentationId, job.target().objectId(), copiedId,
				job.target().transform(), job.target().chartIdInSheet());
	}

	/**
	 * Builds an empty pivot, used as the fallback for a sheet-sourced tactic whose pacing block
	 * was absent so it is skipped rather than charted.
	 *
	 * @return a pivot with no data and no clicks/completions series
	 */
	Pivot emptyPivot() {
		return new Pivot(new LinkedHashMap<>(), false, false);
	}

}
