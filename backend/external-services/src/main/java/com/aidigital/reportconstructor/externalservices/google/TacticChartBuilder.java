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

	public TacticChartBuilder(
			ChartPivot chartPivot,
			ChartSheetWriter chartSheetWriter,
			ChartSpecBuilder chartSpecBuilder,
			SlideChartSwapper slideChartSwapper,
			DriveCopier driveCopier,
			ChartErrorTranslator chartErrors,
			ChartTemplateCatalog templates,
			TacticLineItemGrouper lineItemGrouper) {
		this.chartPivot = chartPivot;
		this.chartSheetWriter = chartSheetWriter;
		this.chartSpecBuilder = chartSpecBuilder;
		this.slideChartSwapper = slideChartSwapper;
		this.driveCopier = driveCopier;
		this.chartErrors = chartErrors;
		this.templates = templates;
		this.lineItemGrouper = lineItemGrouper;
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

		Headers headers = chartPivot.parseBqHeaders(req.bqRows());

		errors.addAll(buildDailyCharts(clients, req, headers, folderId));
		errors.addAll(buildMonthlyCharts(clients, req, headers, folderId));
		errors.addAll(buildDistributionCharts(clients, req, folderId));
		return errors;
	}

	/**
	 * Builds the daily combo chart for each requested tactic.
	 *
	 * @param clients  the Google clients for this request
	 * @param req      the chart request
	 * @param headers  parsed BigQuery column indices
	 * @param folderId the Drive output folder id, or {@code null} for the root
	 * @return per-chart error strings (empty on full success)
	 */
	List<String> buildDailyCharts(ChartClients clients, ChartRequest req, Headers headers, String folderId) {
		List<String> errors = new ArrayList<>();
		if (!headers.valid()) {
			errors.add("Daily: BQ sheet — Date or Impressions column not found");
			return errors;
		}
		Map<Integer, List<String>> tacticLineItems = lineItemGrouper.groupByTactic(req.lineItemMapping());
		Map<String, ElementTransform> transforms =
				slideChartSwapper.loadTransforms(clients.slides(), req.presentationId(), errors, "Daily");

		for (int n = 1; n <= req.tacticCount(); n++) {
			List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
			try {
				Pivot pivot = chartPivot.buildDailyPivot(req.bqRows(), liIds, headers, req.flightTs());
				if (pivot.isEmpty()) {
					errors.add("Tactic " + n + ": no BQ data (line item ids: " + String.join(",", liIds) + ")");
					continue;
				}
				renderComboChart(clients, req.presentationId(), folderId, transforms, errors,
						new ComboChartJob(
								templates.getDailyTemplateSheetIds().get(n),
								templates.getDailySlideObjectIds().get(n),
								"Chart Tactic " + n + " — " + req.campaignTitle(),
								pivot,
								"Tactic " + n));
			} catch (IOException ex) {
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
	 * @return per-chart error strings (empty on full success)
	 */
	List<String> buildMonthlyCharts(ChartClients clients, ChartRequest req, Headers headers, String folderId) {
		List<String> errors = new ArrayList<>();
		if (!headers.valid()) {
			errors.add("Monthly: BQ sheet — Date or Impressions column not found");
			return errors;
		}
		Map<Integer, List<String>> tacticLineItems = lineItemGrouper.groupByTactic(req.lineItemMapping());
		boolean multiYear = chartPivot.isMultiYear(req.bqRows(), headers, req.flightTs());
		Map<String, ElementTransform> transforms =
				slideChartSwapper.loadTransforms(clients.slides(), req.presentationId(), errors, "Monthly");

		for (int n = 1; n <= req.tacticCount(); n++) {
			List<String> liIds = tacticLineItems.getOrDefault(n, List.of());
			try {
				Pivot pivot = chartPivot.buildMonthlyPivot(req.bqRows(), liIds, headers, req.flightTs(), multiYear);
				if (pivot.isEmpty()) {
					errors.add("Monthly Tactic " + n + ": no data (line item ids: " + String.join(",", liIds) + ")");
					continue;
				}
				renderComboChart(clients, req.presentationId(), folderId, transforms, errors,
						new ComboChartJob(
								templates.getMonthlyTemplateSheetIds().get(n),
								templates.getMonthlySlideObjectIds().get(n),
								"Monthly Chart Tactic " + n + " — " + req.campaignTitle(),
								pivot,
								"Monthly Tactic " + n));
			} catch (IOException ex) {
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
	 * @return per-chart error strings (empty on full success)
	 */
	List<String> buildDistributionCharts(ChartClients clients, ChartRequest req, String folderId) {
		List<String> errors = new ArrayList<>();
		Map<String, ElementTransform> transforms =
				slideChartSwapper.loadTransforms(clients.slides(), req.presentationId(), errors, "Distribution");

		for (int n = 1; n <= req.tacticCount(); n++) {
			try {
				renderDistributionChart(clients, req.presentationId(), folderId, transforms,
						new DistributionChartJob(
								n,
								templates.getDistTemplateSheetIds().get(n),
								templates.getDistSlideObjectIds().get(n),
								"Distribution Chart Tactic " + n + " — " + req.campaignTitle(),
								req.distTacticNames().getOrDefault(n, "Tactic " + n),
								req.distTacticImps().getOrDefault(n, 0.0),
								req.distTotalImps()));
			} catch (IOException ex) {
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
	 * @param transforms     slide element transforms keyed by object id
	 * @param errors         collector for non-fatal per-chart errors
	 * @param job            the combo chart inputs
	 * @throws IOException when a Google API call fails irrecoverably
	 */
	void renderComboChart(
			ChartClients clients,
			String presentationId,
			String folderId,
			Map<String, ElementTransform> transforms,
			List<String> errors,
			ComboChartJob job) throws IOException {
		if (job.templateId() == null) {
			errors.add(job.tag() + ": no chart-template spreadsheet id configured");
			return;
		}
		if (job.oldObjectId() == null) {
			errors.add(job.tag() + ": no slide chart object id configured");
			return;
		}
		String copiedId = driveCopier.copyFile(clients.drive(), job.templateId(), job.copyName(), folderId);
		ChartSpec spec = chartSpecBuilder.readChartSpec(clients.sheets(), job.templateId());
		String tab = chartSpecBuilder.findDataTab(clients.sheets(), copiedId);
		chartSheetWriter.writePivot(clients.sheets(), copiedId, tab, job.pivot());
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
				clients.slides(), presentationId, job.oldObjectId(), copiedId, transforms.get(job.oldObjectId()));
	}

	/**
	 * Renders one distribution (pie) chart: copies the template, writes the slice values,
	 * re-applies the pie colours and swaps the placeholder chart on the slide.
	 *
	 * @param clients        the Google clients for this request
	 * @param presentationId the deck whose placeholder chart is replaced
	 * @param folderId       the Drive output folder id, or {@code null} for the root
	 * @param transforms     slide element transforms keyed by object id
	 * @param job            the distribution chart inputs
	 * @throws IOException when a Google API call fails irrecoverably
	 */
	void renderDistributionChart(
			ChartClients clients,
			String presentationId,
			String folderId,
			Map<String, ElementTransform> transforms,
			DistributionChartJob job) throws IOException {
		String copiedId = driveCopier.copyFile(clients.drive(), job.templateId(), job.copyName(), folderId);
		ChartSpec spec = chartSpecBuilder.readChartSpec(clients.sheets(), job.templateId());
		String tab = chartSpecBuilder.findDataTab(clients.sheets(), copiedId);
		chartSheetWriter.writeDistribution(
				clients.sheets(), copiedId, tab, job.tacticName(), job.tacticImp(), job.otherImps());
		if (spec != null) {
			try {
				chartSpecBuilder.applyChartSpec(
						clients.sheets(), copiedId, chartSpecBuilder.injectPieSliceColors(spec));
			} catch (IOException colorEx) {
				log.warn("[charts] distribution tactic {} slice recolor skipped (non-fatal): {}",
						job.tacticNum(), colorEx.getMessage());
			}
		}
		slideChartSwapper.replaceChartOnSlide(
				clients.slides(), presentationId, job.oldObjectId(), copiedId, transforms.get(job.oldObjectId()));
	}

}
