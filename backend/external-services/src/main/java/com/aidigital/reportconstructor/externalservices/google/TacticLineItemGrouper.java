package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the flat tactic-to-line-item mapping from a generation request into per-tactic
 * line item id lists, used to filter BigQuery rows when building each tactic's charts.
 */
@Component
public class TacticLineItemGrouper {

	/**
	 * Groups line item ids by their 1-based tactic number, preserving encounter order and
	 * skipping entries with a non-positive tactic number or a blank line item id.
	 *
	 * @param mapping the flat tactic-number / line-item-id pairs from the generation request
	 *                (may be {@code null})
	 * @return a map from tactic number to its line item ids; empty when {@code mapping} is
	 * {@code null} or contains no usable entries
	 */
	public Map<Integer, List<String>> groupByTactic(List<LineItemMapping> mapping) {
		Map<Integer, List<String>> out = new LinkedHashMap<>();
		if (mapping == null) {
			return out;
		}
		for (LineItemMapping m : mapping) {
			int n = m.tacticNum() == null ? 0 : m.tacticNum();
			String liId = m.lineItemId() == null ? "" : m.lineItemId().trim();
			if (n > 0 && !liId.isEmpty()) {
				out.computeIfAbsent(n, k -> new ArrayList<>()).add(liId);
			}
		}
		return out;
	}
}
