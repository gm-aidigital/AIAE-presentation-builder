package com.aidigital.reportconstructor.service.reports.services;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;

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
	 * Generate path — builds the flat {@code {{token}} → value} map used to fill the Slides deck.
	 *
	 * @param payload    the constructor request carrying the Media-Plan, Adjustments and other input rows
	 * @param data       the aggregated campaign/tactic metrics snapshot
	 * @param ccA        Claude Batch A output (strategic copy)
	 * @param ccB        Claude Batch B output (per-tactic copy)
	 * @param ccC        Claude Batch C output (results copy)
	 * @param geoSummary AI-generated geo summary, or {@code null} when the Geo tab is not used
	 * @return the ordered map of double-brace placeholder tokens to their final replacement strings
	 */
	Map<String, String> buildFlatReplacements(
			GeneratePayload payload,
			CampaignData data,
			ClaudeStrategic ccA,
			ClaudeTactical ccB,
			ClaudeResults ccC,
			String geoSummary
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
}
