package com.aidigital.reportconstructor.service.reports.helpers;

import java.util.List;
import java.util.Map;

/**
 * Reverse of the "Generate Sheet" fill step: reads the scalar {@code {{token}} → value}
 * pairs back out of a filled (and possibly user-edited) EOC workbook grid, so the
 * "Slides from Sheet" flow can drive the deck straight from the sheet the user reviewed.
 *
 * <p>Every value is located by <em>label/header anchor</em>, never by a fixed cell
 * reference, so the read survives rows or columns the user inserts while editing. The
 * three structural regions of the EOC template are handled separately:
 *
 * <ol>
 *   <li>the top info block — value in the cell to the right of each {@code "Client name:"}-style
 *       label (and {@code {{RFP info}}} beneath the {@code "RFP Input"} header);</li>
 *   <li>the per-tactic summary table — anchored on its {@code "Tactic name"/"Benchmark"/…}
 *       header row, one data row per tactic down to the {@code "Total"} row;</li>
 *   <li>the per-tactic "Main slide" detail blocks — each anchored by a {@code "Main slide N"} cell
 *       that keys the block to tactic {@code N}, each field located by its in-block label
 *       ({@code "Tactic Goal"}, {@code "Weekdays"}, {@code "Male"}, …).</li>
 * </ol>
 *
 * <p>Narrative placeholders the sheet does not carry (strategic points, recommendations,
 * the {@code f_*} story copy, per-tactic overviews) are not produced here — they are the
 * Claude executive batch's job in the slides step.
 */
public interface SheetPlaceholderReader {

	/**
	 * Reads all sheet-derived scalar placeholders from a filled EOC workbook grid.
	 *
	 * @param grid the first tab of the filled workbook, as trimmed cell strings (may be {@code null})
	 * @return an ordered {@code {{token}} → value} map for every value found; unreplaced template
	 *         tokens are skipped, so callers can merge Claude-produced narrative on top without clashing
	 */
	Map<String, String> readPlaceholders(List<List<String>> grid);
}
