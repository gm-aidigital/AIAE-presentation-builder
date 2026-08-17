package com.aidigital.reportconstructor.externalservices.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
	private String dailyTemplateSheetId = "";
	private String monthlyTemplateSheetId = "";
	private String distTemplateSheetId = "";
	private Integer dailyChartIdInSheet;
	private Integer monthlyChartIdInSheet;
	private Integer distChartIdInSheet;
	private Map<Integer, String> dailyTemplateSheetIds = Map.of();
	private Map<Integer, String> dailySlideObjectIds = Map.of();
	private Map<Integer, String> monthlyTemplateSheetIds = Map.of();
	private Map<Integer, String> monthlySlideObjectIds = Map.of();
	private Map<Integer, String> distTemplateSheetIds = Map.of();
	private Map<Integer, String> distSlideObjectIds = Map.of();
	private RgbColor comboColumnColor;
	private RgbColor comboLineColor;

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
	 * Returns the single daily-chart template spreadsheet the master model uses for every tactic; blank when
	 * unconfigured, which keeps the legacy per-slot map in charge.
	 *
	 * @return the daily-chart template spreadsheet id, or blank when unconfigured
	 */
	public String getDailyTemplateSheetId() {
		return dailyTemplateSheetId;
	}

	/**
	 * Sets the single daily-chart template spreadsheet id, normalizing null to blank ("unconfigured").
	 *
	 * @param dailyTemplateSheetId the daily-chart template spreadsheet id (may be null)
	 */
	public void setDailyTemplateSheetId(String dailyTemplateSheetId) {
		this.dailyTemplateSheetId = dailyTemplateSheetId == null ? "" : dailyTemplateSheetId.trim();
	}

	/**
	 * Returns the single monthly-chart template spreadsheet the master model uses for every tactic; blank
	 * when unconfigured.
	 *
	 * @return the monthly-chart template spreadsheet id, or blank when unconfigured
	 */
	public String getMonthlyTemplateSheetId() {
		return monthlyTemplateSheetId;
	}

	/**
	 * Sets the single monthly-chart template spreadsheet id, normalizing null to blank ("unconfigured").
	 *
	 * @param monthlyTemplateSheetId the monthly-chart template spreadsheet id (may be null)
	 */
	public void setMonthlyTemplateSheetId(String monthlyTemplateSheetId) {
		this.monthlyTemplateSheetId = monthlyTemplateSheetId == null ? "" : monthlyTemplateSheetId.trim();
	}

	/**
	 * Returns the single distribution-chart template spreadsheet the master model uses for every tactic;
	 * blank when unconfigured.
	 *
	 * @return the distribution-chart template spreadsheet id, or blank when unconfigured
	 */
	public String getDistTemplateSheetId() {
		return distTemplateSheetId;
	}

	/**
	 * Sets the single distribution-chart template spreadsheet id, normalizing null to blank ("unconfigured").
	 *
	 * @param distTemplateSheetId the distribution-chart template spreadsheet id (may be null)
	 */
	public void setDistTemplateSheetId(String distTemplateSheetId) {
		this.distTemplateSheetId = distTemplateSheetId == null ? "" : distTemplateSheetId.trim();
	}

	/**
	 * Returns the daily source workbook's own embedded chart id, or {@code null} to fall back to the shared
	 * {@link #getChartIdInSheet()}.
	 *
	 * @return the daily in-sheet chart id override, or {@code null} when unset
	 */
	public Integer getDailyChartIdInSheet() {
		return dailyChartIdInSheet;
	}

	/**
	 * Sets the daily source workbook's embedded chart id.
	 *
	 * @param dailyChartIdInSheet the in-sheet chart id, or null to use the shared default
	 */
	public void setDailyChartIdInSheet(Integer dailyChartIdInSheet) {
		this.dailyChartIdInSheet = dailyChartIdInSheet;
	}

	/**
	 * Returns the monthly source workbook's own embedded chart id, or {@code null} for the shared default.
	 *
	 * @return the monthly in-sheet chart id override, or {@code null} when unset
	 */
	public Integer getMonthlyChartIdInSheet() {
		return monthlyChartIdInSheet;
	}

	/**
	 * Sets the monthly source workbook's embedded chart id.
	 *
	 * @param monthlyChartIdInSheet the in-sheet chart id, or null to use the shared default
	 */
	public void setMonthlyChartIdInSheet(Integer monthlyChartIdInSheet) {
		this.monthlyChartIdInSheet = monthlyChartIdInSheet;
	}

	/**
	 * Returns the distribution source workbook's own embedded chart id, or {@code null} for the shared default.
	 *
	 * @return the distribution in-sheet chart id override, or {@code null} when unset
	 */
	public Integer getDistChartIdInSheet() {
		return distChartIdInSheet;
	}

	/**
	 * Sets the distribution source workbook's embedded chart id.
	 *
	 * @param distChartIdInSheet the in-sheet chart id, or null to use the shared default
	 */
	public void setDistChartIdInSheet(Integer distChartIdInSheet) {
		this.distChartIdInSheet = distChartIdInSheet;
	}

	/**
	 * Resolves which embedded chart to pull out of a copied daily source workbook: the per-type id when
	 * configured, otherwise the shared {@link #getChartIdInSheet()}.
	 *
	 * <p>Per-type ids exist because a workbook's chart id is assigned by Sheets when the chart is created —
	 * three separately built workbooks have no reason to agree on it, and the legacy 84 workbooks only did
	 * because they were copies of one another.
	 *
	 * @return the daily in-sheet chart id
	 */
	public int dailyChartId() {
		return dailyChartIdInSheet == null ? chartIdInSheet : dailyChartIdInSheet;
	}

	/**
	 * Resolves which embedded chart to pull out of a copied monthly source workbook.
	 *
	 * @return the monthly in-sheet chart id
	 */
	public int monthlyChartId() {
		return monthlyChartIdInSheet == null ? chartIdInSheet : monthlyChartIdInSheet;
	}

	/**
	 * Resolves which embedded chart to pull out of a copied distribution source workbook.
	 *
	 * @return the distribution in-sheet chart id
	 */
	public int distChartId() {
		return distChartIdInSheet == null ? chartIdInSheet : distChartIdInSheet;
	}

	/**
	 * Resolves the daily-chart template spreadsheet for one tactic: the single master-model workbook when
	 * configured, otherwise that tactic's entry in the legacy per-slot map.
	 *
	 * @param tacticNum the 1-based tactic number
	 * @return the template spreadsheet id, or {@code null} when neither is configured
	 */
	public String dailyTemplateFor(int tacticNum) {
		return dailyTemplateSheetId.isBlank() ? dailyTemplateSheetIds.get(tacticNum) : dailyTemplateSheetId;
	}

	/**
	 * Resolves the monthly-chart template spreadsheet for one tactic, single workbook first.
	 *
	 * @param tacticNum the 1-based tactic number
	 * @return the template spreadsheet id, or {@code null} when neither is configured
	 */
	public String monthlyTemplateFor(int tacticNum) {
		return monthlyTemplateSheetId.isBlank() ? monthlyTemplateSheetIds.get(tacticNum) : monthlyTemplateSheetId;
	}

	/**
	 * Resolves the distribution-chart template spreadsheet for one tactic, single workbook first.
	 *
	 * @param tacticNum the 1-based tactic number
	 * @return the template spreadsheet id, or {@code null} when neither is configured
	 */
	public String distTemplateFor(int tacticNum) {
		return distTemplateSheetId.isBlank() ? distTemplateSheetIds.get(tacticNum) : distTemplateSheetId;
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
	 * Returns the configured color for combo-chart columns (the Impressions series), or {@code null} to fall back
	 * to the built-in brand default.
	 *
	 * @return the combo column color, or {@code null} when unset
	 */
	public RgbColor getComboColumnColor() {
		return comboColumnColor;
	}

	/**
	 * Sets the combo-chart column (Impressions series) color.
	 *
	 * @param comboColumnColor the normalized RGB color applied to combo columns (may be null to use the default)
	 */
	public void setComboColumnColor(RgbColor comboColumnColor) {
		this.comboColumnColor = comboColumnColor;
	}

	/**
	 * Returns the configured color for the combo-chart line (the CTR/VCR rate series), or {@code null} to fall back
	 * to the built-in brand default.
	 *
	 * @return the combo line color, or {@code null} when unset
	 */
	public RgbColor getComboLineColor() {
		return comboLineColor;
	}

	/**
	 * Sets the combo-chart line (CTR/VCR rate series) color.
	 *
	 * @param comboLineColor the normalized RGB color applied to the combo line (may be null to use the default)
	 */
	public void setComboLineColor(RgbColor comboLineColor) {
		this.comboLineColor = comboLineColor;
	}

	/**
	 * Builds the combo column (Impressions series) color as a {@code {red, green, blue}} triple, falling back to the
	 * built-in brand blue ({@code #0009DB}) when none is configured.
	 *
	 * @return the normalized RGB components for combo columns
	 */
	public double[] comboColumnColorComponents() {
		return comboColumnColor == null
				? new double[]{0.0, 0.035294, 0.858824}
				: new double[]{comboColumnColor.getRed(), comboColumnColor.getGreen(), comboColumnColor.getBlue()};
	}

	/**
	 * Builds the combo line (CTR/VCR series) color as a {@code {red, green, blue}} triple, falling back to the
	 * built-in brand green ({@code #AFF23F}) when none is configured.
	 *
	 * @return the normalized RGB components for the combo line
	 */
	public double[] comboLineColorComponents() {
		return comboLineColor == null
				? new double[]{0.686275, 0.949020, 0.247059}
				: new double[]{comboLineColor.getRed(), comboLineColor.getGreen(), comboLineColor.getBlue()};
	}
}
