package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;
import com.aidigital.reportconstructor.service.reports.helpers.EffectiveTacticsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.MediaPlanTacticExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Intersects the media plan's Media column with the confirmed line-item mapping.
 *
 * <p>Ordering follows the mapping's {@code tacticNum} (the report slot), while each entry's
 * {@code planTacticNum} says which original Media-column row it came from — that row supplies the
 * tactic name and the context used to disambiguate duplicate names. A mapping entry pointing outside
 * the plan (hand-edited payload, plan re-read after matching) falls back to the name carried in the
 * mapping itself, so a report is never silently dropped for a bookkeeping mismatch.
 */
@Component
public class EffectiveTacticsHelperImpl implements EffectiveTacticsHelper {

	private final MediaPlanTacticExtractor tacticExtractor;

	public EffectiveTacticsHelperImpl(MediaPlanTacticExtractor tacticExtractor) {
		this.tacticExtractor = tacticExtractor;
	}

	@Override
	public List<PlanTactic> effectiveTactics(List<List<String>> planRows, List<LineItemMapping> mapping) {

		List<PlanTactic> all = tacticExtractor.extract(planRows);
		if (mapping == null || mapping.isEmpty()) {
			return all;
		}
		List<PlanTactic> out = new ArrayList<>();
		for (LineItemMapping m : inReportOrder(mapping)) {
			Integer planNum = m.planNumOrSlot();
			int idx = planNum == null ? -1 : planNum - 1;
			if (idx >= 0 && idx < all.size()) {
				out.add(all.get(idx));
			} else {
				out.add(new PlanTactic(m.tactic() == null ? "" : m.tactic(), ""));
			}
		}
		return out;
	}

	@Override
	public int effectiveTacticCount(List<List<String>> planRows, List<LineItemMapping> mapping) {

		if (mapping != null && !mapping.isEmpty()) {
			return mapping.size();
		}
		return tacticExtractor.extract(planRows).size();
	}

	/**
	 * Sorts mapping entries by their report slot, keeping entries without one last in their original
	 * order, so the effective list mirrors the numbering the deck and sheet are built around.
	 *
	 * @param mapping the confirmed line-item mapping
	 * @return a new list ordered by {@code tacticNum}
	 */
	List<LineItemMapping> inReportOrder(List<LineItemMapping> mapping) {

		List<LineItemMapping> ordered = new ArrayList<>(mapping);
		ordered.sort(Comparator.comparingInt(m -> m.tacticNum() == null ? Integer.MAX_VALUE : m.tacticNum()));
		return ordered;
	}
}
