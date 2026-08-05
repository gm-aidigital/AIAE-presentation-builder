package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMapping;
import com.aidigital.reportconstructor.service.reports.dto.PlanTactic;

import java.util.List;

/**
 * Resolves which media-plan tactics the report actually covers.
 *
 * <p>A media plan often carries more line items than the report is about: at matching time the user
 * can drop the rows they don't want reported on. The confirmed line-item mapping is therefore the
 * authoritative tactic list — it holds exactly the surviving tactics, renumbered 1..N — while the
 * Media column still holds every original row. Every part of the pipeline that used to re-derive the
 * tactic list (or its count) straight from the plan grid goes through this helper instead, so a
 * dropped row is invisible everywhere: no slide, no sheet block, no narrative, no numbers.
 *
 * <p>With no mapping (older payloads, previews that never matched) the full plan list is returned
 * unchanged.
 */
public interface EffectiveTacticsHelper {

	/**
	 * Returns the tactics the report covers, in report order (mapping slot 1..N).
	 *
	 * @param planRows the Media Plan rows
	 * @param mapping  the confirmed line-item mapping, or {@code null}/empty when none was confirmed
	 * @return the surviving plan tactics in report order; the full plan list when there is no mapping
	 */
	List<PlanTactic> effectiveTactics(List<List<String>> planRows, List<LineItemMapping> mapping);

	/**
	 * Returns how many tactics the report covers.
	 *
	 * @param planRows the Media Plan rows
	 * @param mapping  the confirmed line-item mapping, or {@code null}/empty when none was confirmed
	 * @return the number of surviving tactics; the full plan tactic count when there is no mapping
	 */
	int effectiveTacticCount(List<List<String>> planRows, List<LineItemMapping> mapping);
}
