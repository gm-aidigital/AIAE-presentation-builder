package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.AudienceTable;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.dto.GeoTable;
import com.aidigital.reportconstructor.service.reports.dto.PublisherRow;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownInferenceHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Spring bean implementation of {@link BreakdownInferenceHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BreakdownInferenceHelperImpl implements BreakdownInferenceHelper {

	private final ReportSheetHelper sheetHelper;

	@Override
	public List<BreakdownSelection> infer(String sheetUrl, int tacticCount, String userGoogleToken) {
		List<BreakdownSelection> selections = new ArrayList<>();
		if (tacticCount <= 0) {
			return selections;
		}
		Set<Integer> tacticNums = new TreeSet<>();
		for (int n = 1; n <= tacticCount; n++) {
			tacticNums.add(n);
		}
		// Five reads for the whole workbook, not five per tactic: each reader takes the full tactic
		// set and answers from one read of the "Breakdowns" tab.
		Map<Integer, List<PublisherRow>> publishers =
				sheetHelper.readPublisherTables(sheetUrl, tacticNums, userGoogleToken);
		Map<Integer, CreativeTable> creatives = sheetHelper.readCreativeTables(sheetUrl, tacticNums, userGoogleToken);
		Map<Integer, GeoTable> geos = sheetHelper.readGeoTables(sheetUrl, tacticNums, userGoogleToken);
		Map<Integer, AudienceTable> audiences = sheetHelper.readAudienceTables(sheetUrl, tacticNums, userGoogleToken);
		Map<Integer, DeviceTable> devices = sheetHelper.readDeviceTables(sheetUrl, tacticNums, userGoogleToken);

		for (Integer tacticNum : tacticNums) {
			Set<BreakdownType> enabled = new LinkedHashSet<>();
			List<PublisherRow> rows = publishers.get(tacticNum);
			if (rows != null && !rows.isEmpty()) {
				enabled.add(BreakdownType.TOP_PUBLISHERS);
			}
			addIfFilled(enabled, BreakdownType.CREATIVE, filled(creatives.get(tacticNum)));
			addIfFilled(enabled, BreakdownType.GEO, filled(geos.get(tacticNum)));
			addIfFilled(enabled, BreakdownType.AUDIENCE, filled(audiences.get(tacticNum)));
			addIfFilled(enabled, BreakdownType.DEVICE, filled(devices.get(tacticNum)));
			selections.add(new BreakdownSelection(tacticNum, codes(enabled)));
		}
		if (log.isInfoEnabled()) {
			log.info("[breakdowns] inferred selections for {} tactic(s) from {}: {}",
					tacticCount, sheetUrl, selections);
		}
		return selections;
	}

	/**
	 * Adds a section to the enabled set when its table carries data.
	 *
	 * @param enabled the accumulating set of enabled sections
	 * @param type    the section under test
	 * @param filled  whether that section's table carries data
	 */
	void addIfFilled(Set<BreakdownType> enabled, BreakdownType type, boolean filled) {
		if (filled) {
			enabled.add(type);
		}
	}

	/**
	 * Whether a creative table carries anything the user typed.
	 *
	 * @param table the tactic's creative table, possibly {@code null}
	 * @return true when the block is present and not blank
	 */
	boolean filled(CreativeTable table) {
		return table != null && !table.isEmpty();
	}

	/**
	 * Whether a geo table carries anything the user typed.
	 *
	 * @param table the tactic's geo table, possibly {@code null}
	 * @return true when the block is present and not blank
	 */
	boolean filled(GeoTable table) {
		return table != null && !table.isEmpty();
	}

	/**
	 * Whether an audience table carries anything the user typed.
	 *
	 * @param table the tactic's audience table, possibly {@code null}
	 * @return true when the block is present and not blank
	 */
	boolean filled(AudienceTable table) {
		return table != null && !table.isEmpty();
	}

	/**
	 * Whether a device table carries anything the user typed.
	 *
	 * @param table the tactic's device table, possibly {@code null}
	 * @return true when the block is present and not blank
	 */
	boolean filled(DeviceTable table) {
		return table != null && !table.isEmpty();
	}

	/**
	 * Renders the enabled sections as the wire codes the generate payload carries.
	 *
	 * @param enabled the enabled sections, in template order
	 * @return their lowercase codes
	 */
	List<String> codes(Set<BreakdownType> enabled) {
		List<String> codes = new ArrayList<>();
		for (BreakdownType type : enabled) {
			codes.add(type.code());
		}
		return codes;
	}
}
