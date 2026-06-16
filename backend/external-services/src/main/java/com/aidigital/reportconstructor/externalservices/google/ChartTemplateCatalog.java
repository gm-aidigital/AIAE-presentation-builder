package com.aidigital.reportconstructor.externalservices.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Google chart helper spreadsheet and slide object ids — bound from
 * {@code external.google.charts.*}. Defaults are empty; configure via env/yaml
 * (see {@code application-local.yml} for legacy POC ids).
 */
@Component
@ConfigurationProperties(prefix = "external.google.charts")
public class ChartTemplateCatalog {

	private int chartIdInSheet;
	private Map<Integer, String> dailyTemplateSheetIds = Map.of();
	private Map<Integer, String> dailySlideObjectIds = Map.of();
	private Map<Integer, String> monthlyTemplateSheetIds = Map.of();
	private Map<Integer, String> monthlySlideObjectIds = Map.of();
	private Map<Integer, String> distTemplateSheetIds = Map.of();
	private Map<Integer, String> distSlideObjectIds = Map.of();
	private List<RgbColor> pieDefaultColors = List.of();

	/**
	 * Returns the embedded chart's object id within its template spreadsheet, used to locate the
	 * chart when copying it into Slides.
	 *
	 * @return the chart object id inside the source sheet
	 */
	public int getChartIdInSheet() {
		return chartIdInSheet;
	}

	/**
	 * Sets the embedded chart's object id within its template spreadsheet.
	 *
	 * @param chartIdInSheet the chart object id inside the source sheet
	 */
	public void setChartIdInSheet(int chartIdInSheet) {
		this.chartIdInSheet = chartIdInSheet;
	}

	/**
	 * Returns the template spreadsheet ids for daily charts, keyed by tactic/placeholder index.
	 *
	 * @return map of tactic index to daily-chart template spreadsheet id
	 */
	public Map<Integer, String> getDailyTemplateSheetIds() {
		return dailyTemplateSheetIds;
	}

	/**
	 * Sets the daily-chart template spreadsheet ids, defaulting to an empty map when null.
	 *
	 * @param dailyTemplateSheetIds map of tactic index to daily-chart template spreadsheet id (may be null)
	 */
	public void setDailyTemplateSheetIds(Map<Integer, String> dailyTemplateSheetIds) {
		this.dailyTemplateSheetIds = dailyTemplateSheetIds == null ? Map.of() : dailyTemplateSheetIds;
	}

	/**
	 * Returns the Slides object ids that the rendered daily charts are placed into, keyed by tactic index.
	 *
	 * @return map of tactic index to daily-chart Slides object id
	 */
	public Map<Integer, String> getDailySlideObjectIds() {
		return dailySlideObjectIds;
	}

	/**
	 * Sets the daily-chart Slides object ids, defaulting to an empty map when null.
	 *
	 * @param dailySlideObjectIds map of tactic index to daily-chart Slides object id (may be null)
	 */
	public void setDailySlideObjectIds(Map<Integer, String> dailySlideObjectIds) {
		this.dailySlideObjectIds = dailySlideObjectIds == null ? Map.of() : dailySlideObjectIds;
	}

	/**
	 * Returns the template spreadsheet ids for monthly charts, keyed by tactic/placeholder index.
	 *
	 * @return map of tactic index to monthly-chart template spreadsheet id
	 */
	public Map<Integer, String> getMonthlyTemplateSheetIds() {
		return monthlyTemplateSheetIds;
	}

	/**
	 * Sets the monthly-chart template spreadsheet ids, defaulting to an empty map when null.
	 *
	 * @param monthlyTemplateSheetIds map of tactic index to monthly-chart template spreadsheet id (may be null)
	 */
	public void setMonthlyTemplateSheetIds(Map<Integer, String> monthlyTemplateSheetIds) {
		this.monthlyTemplateSheetIds = monthlyTemplateSheetIds == null ? Map.of() : monthlyTemplateSheetIds;
	}

	/**
	 * Returns the Slides object ids that the rendered monthly charts are placed into, keyed by tactic index.
	 *
	 * @return map of tactic index to monthly-chart Slides object id
	 */
	public Map<Integer, String> getMonthlySlideObjectIds() {
		return monthlySlideObjectIds;
	}

	/**
	 * Sets the monthly-chart Slides object ids, defaulting to an empty map when null.
	 *
	 * @param monthlySlideObjectIds map of tactic index to monthly-chart Slides object id (may be null)
	 */
	public void setMonthlySlideObjectIds(Map<Integer, String> monthlySlideObjectIds) {
		this.monthlySlideObjectIds = monthlySlideObjectIds == null ? Map.of() : monthlySlideObjectIds;
	}

	/**
	 * Returns the template spreadsheet ids for distribution (pie) charts, keyed by tactic/placeholder index.
	 *
	 * @return map of tactic index to distribution-chart template spreadsheet id
	 */
	public Map<Integer, String> getDistTemplateSheetIds() {
		return distTemplateSheetIds;
	}

	/**
	 * Sets the distribution-chart template spreadsheet ids, defaulting to an empty map when null.
	 *
	 * @param distTemplateSheetIds map of tactic index to distribution-chart template spreadsheet id (may be null)
	 */
	public void setDistTemplateSheetIds(Map<Integer, String> distTemplateSheetIds) {
		this.distTemplateSheetIds = distTemplateSheetIds == null ? Map.of() : distTemplateSheetIds;
	}

	/**
	 * Returns the Slides object ids that the rendered distribution (pie) charts are placed into, keyed by tactic
	 * index.
	 *
	 * @return map of tactic index to distribution-chart Slides object id
	 */
	public Map<Integer, String> getDistSlideObjectIds() {
		return distSlideObjectIds;
	}

	/**
	 * Sets the distribution-chart Slides object ids, defaulting to an empty map when null.
	 *
	 * @param distSlideObjectIds map of tactic index to distribution-chart Slides object id (may be null)
	 */
	public void setDistSlideObjectIds(Map<Integer, String> distSlideObjectIds) {
		this.distSlideObjectIds = distSlideObjectIds == null ? Map.of() : distSlideObjectIds;
	}

	/**
	 * Returns the configured fallback palette applied to pie slices when the template defines no colors.
	 *
	 * @return ordered list of RGB colors used as the default pie palette
	 */
	public List<RgbColor> getPieDefaultColors() {
		return pieDefaultColors;
	}

	/**
	 * Sets the fallback pie palette, defaulting to an empty list when null.
	 *
	 * @param pieDefaultColors ordered list of RGB colors used as the default pie palette (may be null)
	 */
	public void setPieDefaultColors(List<RgbColor> pieDefaultColors) {
		this.pieDefaultColors = pieDefaultColors == null ? List.of() : pieDefaultColors;
	}

	/**
	 * Builds the pie slice color matrix as rows of {@code {red, green, blue}} components, falling back
	 * to the built-in Teal/Orange palette when no default colors are configured.
	 *
	 * @return a matrix of normalized RGB component triples, one row per slice color
	 */
	public double[][] pieDefaultColorMatrix() {
		if (pieDefaultColors.isEmpty()) {
			return new double[][]{
					{0.173, 0.490, 0.502},
					{0.937, 0.490, 0.133}
			};
		}
		double[][] out = new double[pieDefaultColors.size()][];
		for (int i = 0; i < pieDefaultColors.size(); i++) {
			RgbColor c = pieDefaultColors.get(i);
			out[i] = new double[]{c.getRed(), c.getGreen(), c.getBlue()};
		}
		return out;
	}
}
