package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;

import java.util.List;
import java.util.Map;

/**
 * Public facade for placeholder preview and Slides replacement map construction.
 */
public interface PlaceholderResolverService {

	/**
	 * Preview path — resolves every section without invoking Claude.
	 *
	 * @param payload the constructor request carrying the Media-Plan, Adjustments and other input rows
	 * @return preview sections plus label chips and resolved/total/sheet/adj counts for the UI
	 */
	PreviewResult resolve(GeneratePayload payload);

	/**
	 * Detects the full min/max date range present in the raw-data (Elevate "Basic" tab) rows so the UI
	 * can show the flight window for the user to confirm or correct before generation. This is the same
	 * range the generation pipeline uses when the confirmed filter is ALL; the media plan is never read.
	 *
	 * @param adjRows the raw-data grid rows uploaded/connected as the Elevate dashboard export
	 * @return the inclusive detected date range, or {@code null} when no dated delivery rows are present
	 */
	FlightDates detectDateRange(List<List<String>> adjRows);

	/**
	 * Generate path — builds the flat {@code {{token}} → value} map used to fill the Slides deck.
	 *
	 * @param payload     the constructor request carrying the Media-Plan, Adjustments and other input rows
	 * @param data        the aggregated campaign/tactic metrics snapshot
	 * @param ccA         Claude Batch A output (strategic copy)
	 * @param ccB         Claude Batch B output (per-tactic copy)
	 * @param ccC         Claude Batch C output (results copy)
	 * @param primaryKpis AI-generated primary-KPIs line, or {@code null} when a manual value is used instead
	 * @param geoSummary  AI-generated geo summary, or {@code null} when the Geo tab is not used
	 * @param frequencies the {@link #computeFrequencies} result for this report, reused so
	 *                    {@code {{reach_f}} / {{reach_f_pres}}} match the actual reach behind {@code {{f_fact}}}
	 * @return the ordered map of double-brace placeholder tokens to their final replacement strings
	 */
	Map<String, String> buildFlatReplacements(
			GeneratePayload payload,
			CampaignData data,
			ClaudeStrategic ccA,
			ClaudeTactical ccB,
			ClaudeResults ccC,
			String primaryKpis,
			String geoSummary,
			CampaignFrequencies frequencies
	);

	/**
	 * Single-pass campaign aggregation shared between preview and generate paths.
	 *
	 * @param payload the constructor request supplying sheet, adjustments, audience,
	 *                estimates rows and the line-item-to-tactic mapping
	 * @return the aggregated campaign/tactic metrics snapshot derived from those rows
	 */
	CampaignData collectData(GeneratePayload payload);

	/**
	 * Computes the planned/actual campaign frequency figures fed into Claude Batch C so the frequency
	 * narrative ({@code {{f_oppartunity}} / {{f_fact}} / {{f_storytelling}}}) embeds the exact numbers.
	 *
	 * @param payload the constructor request carrying the Media-Plan, Adjustments and Estimates rows
	 * @param data    the aggregated campaign snapshot supplying the impression total
	 * @return the computed frequency figures; either field may be {@code null} when reach/impressions are absent
	 */
	CampaignFrequencies computeFrequencies(GeneratePayload payload, CampaignData data);

	/**
	 * Gates Claude Batch A when strategic placeholders are absent from both row sets.
	 *
	 * @param payload the constructor request whose adjustments and sheet rows are inspected
	 * @return {@code true} if at least one Batch A placeholder is still unresolved
	 */
	boolean needStrategic(GeneratePayload payload);

	/**
	 * Gates Claude Batch B when per-tactic gender or weekday copy is missing.
	 *
	 * @param payload the constructor request whose adjustments and sheet rows are inspected
	 * @param data    the aggregated snapshot whose tactic keys drive the per-tactic checks
	 * @return {@code true} if at least one tactic still needs Batch B copy generated
	 */
	boolean needTactical(GeneratePayload payload, CampaignData data);

	/**
	 * Gates Claude Batch C when results or tactic overview copy is missing.
	 *
	 * @param payload the constructor request whose adjustments and sheet rows are inspected
	 * @param data    the aggregated snapshot whose tactic keys drive the per-tactic overview checks
	 * @return {@code true} if at least one Batch C placeholder is still unresolved
	 */
	boolean needResults(GeneratePayload payload, CampaignData data);

	/**
	 * Gates the AI geo summary when manual geo locations are absent and the Geo tab is referenced.
	 *
	 * @param payload the constructor request whose adjustments and sheet rows are inspected
	 * @return {@code true} if the geo summary must be generated from the Geo tab
	 */
	boolean needGeoSummary(GeneratePayload payload);

	/**
	 * Gates the AI primary-KPIs generation when no manual primary-KPIs value is present.
	 *
	 * @param payload the constructor request whose adjustments and sheet rows are inspected
	 * @return {@code true} if the primary KPIs must be generated by Claude from the media plan
	 */
	boolean needPrimaryKpis(GeneratePayload payload);
}
