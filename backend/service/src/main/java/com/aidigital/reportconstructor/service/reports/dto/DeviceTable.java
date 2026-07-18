package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/**
 * One tactic's hand-entered "Device breakdown" block on the generated workbook's "Breakdowns" tab:
 * the five stat tiles above the table plus the "PERFORMANCE BY DEVICE" table's filled rows.
 *
 * <p>The stat tiles are read as typed rather than derived from {@link #rows()} — the user may scope
 * them differently from the four-device table (e.g. highest CTR across every device, not just the
 * four shown) — so recomputing them here would contradict the sheet the user reviewed. The device
 * rows land on the slide's fixed per-device tokens, matched by the device label, not by row order.
 *
 * @param highestCtr             the {@code HIGHEST CTR} stat tile ({@code {{dev_N_ctr}}})
 * @param bestCompletion         the {@code BEST COMPLETION} stat tile ({@code {{dev_N_vcr}}})
 * @param devicesTracked         the {@code DEVICES TRACKED} stat tile ({@code {{dev_N_amount}}})
 * @param topDevice              the {@code TOP DEVICE} stat tile ({@code {{top_dev_N}}})
 * @param topDeviceImpressionsPct the {@code TOP DEVICE - % OF IMPRESSIONS} stat tile
 *                               ({@code {{dev_proc_imps_N}}})
 * @param rows                   the table's filled rows, in sheet order; never padded with empty rows
 */
public record DeviceTable(
		String highestCtr, String bestCompletion, String devicesTracked, String topDevice,
		String topDeviceImpressionsPct, List<DeviceRow> rows) {

	/**
	 * The empty table, used for a tactic whose block is missing from the sheet or was left entirely
	 * blank, so callers never have to null-check the block itself. Immutable and safely shared.
	 */
	public static final DeviceTable EMPTY = new DeviceTable("", "", "", "", "", List.of());

	/**
	 * Reports whether the user filled in nothing at all for this tactic. Such a tactic still gets its
	 * slide (the toggle was on) but is never sent to Claude — there would be nothing to observe.
	 *
	 * @return true when every stat tile is blank and the table carries no rows
	 */
	public boolean isEmpty() {
		return rows.isEmpty()
				&& (highestCtr == null || highestCtr.isBlank())
				&& (bestCompletion == null || bestCompletion.isBlank())
				&& (devicesTracked == null || devicesTracked.isBlank())
				&& (topDevice == null || topDevice.isBlank())
				&& (topDeviceImpressionsPct == null || topDeviceImpressionsPct.isBlank());
	}
}
