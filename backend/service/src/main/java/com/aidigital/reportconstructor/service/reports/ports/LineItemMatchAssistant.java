package com.aidigital.reportconstructor.service.reports.ports;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchOption;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchTactic;

import java.util.List;
import java.util.Map;

/**
 * AI-assisted disambiguation of media-plan tactics to BigQuery line items, used only when the
 * deterministic unique-ID rule leaves a channel ambiguous (several tactics and several line item IDs
 * sharing one channel).
 *
 * <p>The real implementation calls Claude; the stub (active when {@code ANTHROPIC_API_KEY} is unset)
 * returns an empty map so matching degrades to manual drag-and-drop.
 */
@FunctionalInterface
public interface LineItemMatchAssistant {

	/**
	 * Assigns line item IDs to tactics within their shared channel, based on semantic similarity
	 * between each tactic's context and each line item's naming.
	 *
	 * @param tactics the ambiguous tactics needing an ID (each carries its expected channel + context)
	 * @param options the unassigned candidate line items (each carries its channel + naming)
	 * @return a map of tactic number &rarr; chosen line item ID; only confident assignments are present,
	 *         unresolved tactics are omitted, and the map is empty when the assistant is stubbed or fails
	 */
	Map<Integer, String> match(List<LineItemMatchTactic> tactics, List<LineItemMatchOption> options);
}
