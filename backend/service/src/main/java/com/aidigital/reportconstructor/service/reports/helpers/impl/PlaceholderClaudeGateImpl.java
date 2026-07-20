package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.engine.MediaPlanColumn;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderClaudeGate;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring bean implementation of {@link PlaceholderClaudeGate}.
 */
@Component
@RequiredArgsConstructor
public class PlaceholderClaudeGateImpl implements PlaceholderClaudeGate {

	private final SheetRowHelper sheetUtils;

	@Override
	public boolean needStrategic(GeneratePayload payload) {
		List<List<String>> adj = payload.adjRows();
		List<List<String>> sheet = payload.sheetRows();
		if (bothNull(adj, sheet, "Audience age:")) {
			return true;
		}
		if (bothNull(adj, sheet, "Audience segments:")) {
			return true;
		}
		if (bothNull(adj, sheet, "Proposal overview:")) {
			return true;
		}
		for (int i = 1; i <= 4; i++) {
			if (bothNull(adj, sheet, "Strategic point " + i + ":")) {
				return true;
			}
			if (bothNull(adj, sheet, "Strategic overview " + i + ":")) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean needTactical(GeneratePayload payload, CampaignData data) {
		if (data == null || data.tactics() == null) {
			return false;
		}
		List<List<String>> adj = payload.adjRows();
		List<List<String>> sheet = payload.sheetRows();
		for (int n : data.tactics().keySet()) {
			if (bothNull(adj, sheet, "Tactic " + n + " male:")
					|| bothNull(adj, sheet, "Tactic " + n + " weekdays:")) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean needResults(GeneratePayload payload, CampaignData data) {
		List<List<String>> adj = payload.adjRows();
		List<List<String>> sheet = payload.sheetRows();
		if (bothNull(adj, sheet, "Our results overview 1:") && bothNull(adj, sheet, "Our results overview:")) {
			return true;
		}
		if (bothNull(adj, sheet, "Thoughts on the performance:")) {
			return true;
		}
		if (bothNull(adj, sheet, "Frequency opportunity:")
				|| bothNull(adj, sheet, "Frequency fact:")
				|| bothNull(adj, sheet, "Frequency storytelling:")) {
			return true;
		}
		for (int i = 1; i <= 4; i++) {
			if (bothNull(adj, sheet, "Recommendation " + i + ":")) {
				return true;
			}
			if (bothNull(adj, sheet, "Recommendation " + i + " text:")) {
				return true;
			}
		}
		if (data != null && data.tactics() != null) {
			for (int n : data.tactics().keySet()) {
				if (bothNull(adj, sheet, "Tactic " + n + " overview:")) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean needGeoSummary(GeneratePayload payload) {
		List<List<String>> adj = payload.adjRows();
		List<List<String>> sheet = payload.sheetRows();
		if (sheetUtils.findLabelValue(adj, "Geo locations:") != null) {
			return false;
		}
		if (sheetUtils.findLabelValue(sheet, "Geo locations:") != null) {
			return false;
		}
		List<String> column = sheetUtils.collectColumnValuesBelow(sheet, MediaPlanColumn.GEO.getSynonyms());
		boolean hasLiteralLocations = column.stream().anyMatch(value -> !sheetUtils.referencesGeoTab(value));
		return !hasLiteralLocations;
	}

	@Override
	public boolean needPrimaryKpis(GeneratePayload payload) {
		return bothNull(payload.adjRows(), payload.sheetRows(), "Primary KPIs:");
	}

	/**
	 * Reports whether a label carries no usable manual value in either the Adjustments or Media Plan rows.
	 * A blank value cell counts as absent: the sheet-as-source template lists placeholder labels with empty
	 * value cells, so findLabelValue returns {@code ""} (label present, no value) rather than null. Treating
	 * that blank as missing lets the Claude batch fill the placeholder instead of leaving it empty.
	 *
	 * @param adj   manual Adjustments tab rows (checked first)
	 * @param sheet Media Plan tab rows
	 * @param label the label whose adjacent value cell is inspected
	 * @return {@code true} when neither source supplies a non-blank value for the label
	 */
	boolean bothNull(List<List<String>> adj, List<List<String>> sheet, String label) {
		return isBlank(sheetUtils.findLabelValue(adj, label))
				&& isBlank(sheetUtils.findLabelValue(sheet, label));
	}

	/**
	 * Reports whether a sheet value is null, empty, or whitespace-only.
	 *
	 * @param value the value to inspect (may be null)
	 * @return {@code true} when the value is null or blank
	 */
	boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
