package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import org.springframework.stereotype.Component;

/**
 * Owns the deterministic object id a duplicated per-tactic breakdown slide gets, so the slide-insertion
 * step and the later chart-linking step agree on the same id without one having to tell the other.
 *
 * <p>The id is derived purely from the {@code (breakdown type, tactic)} pair — the same information both
 * sides already hold — which is what lets the chart step find a slide it never created.
 */
@Component
public class BreakdownSlideNaming {

	/**
	 * Builds the deterministic object id for a duplicated breakdown slide, unique per
	 * {@code (breakdown type, tactic)} pair, e.g. {@code bd_dev_3}.
	 *
	 * @param type      the breakdown section
	 * @param tacticNum the 1-based tactic number
	 * @return the copy's slide object id
	 */
	public String slideId(BreakdownType type, int tacticNum) {
		return "bd_" + type.code() + "_" + tacticNum;
	}

	/**
	 * Builds the deterministic object id for a duplicated "Thoughts on tactic performance" slide, unique per
	 * tactic, e.g. {@code thoughts_3}. Kept alongside {@link #slideId(BreakdownType, int)} so all duplicated
	 * per-tactic slide ids are minted in one place.
	 *
	 * @param tacticNum the 1-based tactic number
	 * @return the copy's slide object id
	 */
	public String thoughtsSlideId(int tacticNum) {
		return "thoughts_" + tacticNum;
	}
}
