package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownValues;
import com.aidigital.reportconstructor.service.reports.dto.DeviceInsightInput;
import com.aidigital.reportconstructor.service.reports.dto.DeviceRow;
import com.aidigital.reportconstructor.service.reports.dto.DeviceTable;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceBreakdownHelperImplTest {

	@Mock
	ReportSheetHelper sheetHelper;
	@Mock
	BreakdownSelectionResolver breakdownResolver;
	@Mock
	ClaudeClient claude;

	@InjectMocks
	DeviceBreakdownHelperImpl helper;

	@Test
	void shouldCopySheetValuesVerbatimAndDashTheDeviceSlotsTheUserLeftBlankTest() {
		// Given: tactic 1 filled its five stat tiles and only the Mobile and Desktop rows
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));
		DeviceTable table = new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
				List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"),
						new DeviceRow("Desktop", "300,000", "0.90%", "85%", "$1,000")));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));
		when(claude.batchDeviceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("takeaway", "worked", "flag", "reco")));

		// When:
		Map<String, String> values = helper.buildDeviceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: the five stat tiles are carried across exactly as typed
		assertThat(values.get("{{dev_1_ctr}}")).isEqualTo("1.20%");
		assertThat(values.get("{{dev_1_vcr}}")).isEqualTo("82%");
		assertThat(values.get("{{dev_1_amount}}")).isEqualTo("4");
		assertThat(values.get("{{top_dev_1}}")).isEqualTo("Mobile");
		assertThat(values.get("{{dev_proc_imps_1}}")).isEqualTo("61%");

		// Then: the filled device rows land on their per-device tokens
		assertThat(values.get("{{mobile_imps_1}}")).isEqualTo("1,200,000");
		assertThat(values.get("{{mobile_ctr_1}}")).isEqualTo("1.20%");
		assertThat(values.get("{{mobile_vcr_1}}")).isEqualTo("78%");
		assertThat(values.get("{{mobile_spend_1}}")).isEqualTo("$4,000");
		assertThat(values.get("{{desktop_imps_1}}")).isEqualTo("300,000");

		// Then: the devices the user did not fill in are dashed, so none can ship as a raw token
		assertThat(values.get("{{ctv_imps_1}}")).isEqualTo("—");
		assertThat(values.get("{{ctv_vcr_1}}")).isEqualTo("—");
		assertThat(values.get("{{tablet_imps_1}}")).isEqualTo("—");
		assertThat(values.get("{{tablet_spend_1}}")).isEqualTo("—");
	}

	@Test
	void shouldOmitTheCtvCtrTokenAndMapRowsByDeviceNameNotOrderTest() {
		// Given: the sheet filled the rows out of slide order — Tablet before Mobile — and filled a CTV row
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));
		DeviceTable table = new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
				List.of(new DeviceRow("Tablet", "50,000", "0.70%", "80%", "$200"),
						new DeviceRow("CTV", "900,000", "", "95%", "$6,000"),
						new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000")));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));
		when(claude.batchDeviceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("t", "w", "f", "r")));

		// When:
		Map<String, String> values = helper.buildDeviceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: each row maps by its device label, not by the order it was typed in
		assertThat(values.get("{{tablet_imps_1}}")).isEqualTo("50,000");
		assertThat(values.get("{{mobile_imps_1}}")).isEqualTo("1,200,000");
		assertThat(values.get("{{ctv_imps_1}}")).isEqualTo("900,000");
		assertThat(values.get("{{ctv_vcr_1}}")).isEqualTo("95%");

		// Then: Connected TV carries no CTR token — the slide prints a literal "—" for it
		assertThat(values).doesNotContainKey("{{ctv_ctr_1}}");
		assertThat(values).containsKey("{{mobile_ctr_1}}");
	}

	@Test
	void shouldWriteTheFourFieldsFromClaudesStringsInSlideOrderTest() {
		// Given: a filled block Claude answers with the takeaway, what-worked, watch-out and recommendation
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
						List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000")))));
		when(claude.batchDeviceInsights(any(), eq("brief")))
				.thenReturn(Map.of(1, List.of("the takeaway", "what worked", "the watch-out", "do this next")));

		// When:
		Map<String, String> values = helper.buildDeviceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: each string maps to its own slide token, in order
		assertThat(values.get("{{dev_1_takeaway}}")).isEqualTo("the takeaway");
		assertThat(values.get("{{dev_1_worked}}")).isEqualTo("what worked");
		assertThat(values.get("{{dev_1_flag}}")).isEqualTo("the watch-out");
		assertThat(values.get("{{dev_1_reco}}")).isEqualTo("do this next");
	}

	@Test
	void shouldDashStatTilesStillHoldingTheTemplatesOwnHintTokenTest() {
		// Given: the user overwrote only HIGHEST CTR, leaving the template's {{…}} hint in TOP DEVICE
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));
		DeviceTable table = new DeviceTable("1.20%", "{{dev_n_vcr}}", "{{dev_n_amount}}", "{{top_dev_n}}",
				"{{dev_proc_imps_n}}", List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000")));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(1, table));
		when(claude.batchDeviceInsights(any(), eq("brief"))).thenReturn(Map.of(1, List.of("t", "w", "f", "r")));

		// When:
		Map<String, String> values = helper.buildDeviceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: the untouched hints are dashed rather than shipped as raw tokens
		assertThat(values.get("{{dev_1_ctr}}")).isEqualTo("1.20%");
		assertThat(values.get("{{dev_1_vcr}}")).isEqualTo("—");
		assertThat(values.get("{{top_dev_1}}")).isEqualTo("—");
		assertThat(values.get("{{dev_proc_imps_1}}")).isEqualTo("—");
	}

	@Test
	void shouldNotSendTheTemplatesHintTokensToClaudeAsIfTheyWereValuesTest() {
		// Given: a block whose stat tiles are still the template's hints, with only the table filled in
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));
		List<DeviceRow> rows = List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000"));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new DeviceTable("{{dev_n_ctr}}", "{{dev_n_vcr}}", "{{dev_n_amount}}", "{{top_dev_n}}",
						"{{dev_proc_imps_n}}", rows)));
		when(claude.batchDeviceInsights(any(), eq("brief"))).thenReturn(Map.of(1, List.of("t", "w", "f", "r")));

		// When:
		helper.buildDeviceValues("sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token");

		// Then: Claude sees the hints as absent, so it cannot read "{{dev_n_ctr}}" as a stat value
		ArgumentCaptor<List<DeviceInsightInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchDeviceInsights(captor.capture(), eq("brief"));
		DeviceTable sent = captor.getValue().getFirst().table();
		assertThat(sent.highestCtr()).isEmpty();
		assertThat(sent.topDevice()).isEmpty();
		assertThat(sent.rows()).isEqualTo(rows);
	}

	@Test
	void shouldNotAskClaudeWhenTheTacticsDeviceBlockIsEmptyTest() {
		// Given: tactic 1 enabled the Device breakdown but never filled the block in — only hints remain
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1), "token")).thenReturn(Map.of(
				1, new DeviceTable("{{dev_n_ctr}}", "{{dev_n_vcr}}", "{{dev_n_amount}}", "{{top_dev_n}}",
						"{{dev_proc_imps_n}}", List.of())));

		// When:
		Map<String, String> values =
				helper.buildDeviceValues("sheet-url", selections, Map.of(), "brief", "token").values();

		// Then: Claude is never asked — there is nothing to observe and any copy would be invented
		verifyNoInteractions(claude);

		// Then: the slide still gets its tokens, with blank fields and dashed tiles/rows rather than raw tokens
		assertThat(values.get("{{dev_1_takeaway}}")).isEmpty();
		assertThat(values.get("{{dev_1_reco}}")).isEmpty();
		assertThat(values.get("{{dev_1_ctr}}")).isEqualTo("—");
		assertThat(values.get("{{mobile_imps_1}}")).isEqualTo("—");
	}

	@Test
	void shouldSendOnlyTacticsWithDataToClaudeAndCarryTheirNameTest() {
		// Given: tactic 1 has a filled block, tactic 2 enabled the toggle but left its block empty
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("dev")), new BreakdownSelection(2, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.DEVICE),
				2, EnumSet.of(BreakdownType.DEVICE)));
		DeviceTable filled = new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
				List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000")));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, DeviceTable.EMPTY));
		when(claude.batchDeviceInsights(any(), eq("brief"))).thenReturn(Map.of(1, List.of("t", "w", "f", "r")));

		// When:
		Map<String, String> values = helper.buildDeviceValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"),
				"brief", "token").values();

		// Then: only tactic 1 is sent, carrying its deck name
		ArgumentCaptor<List<DeviceInsightInput>> captor = ArgumentCaptor.captor();
		verify(claude).batchDeviceInsights(captor.capture(), eq("brief"));
		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().getFirst().tacticNum()).isEqualTo(1);
		assertThat(captor.getValue().getFirst().tacticName()).isEqualTo("CTV");

		// Then: tactic 1 gets its fields and tactic 2's are blanked
		assertThat(values.get("{{dev_1_takeaway}}")).isEqualTo("t");
		assertThat(values.get("{{dev_2_takeaway}}")).isEmpty();
		assertThat(values.get("{{dev_2_reco}}")).isEmpty();
	}

	@Test
	void shouldWarnOnlyForTheTacticWhoseFilledBlockGotNoFieldsTest() {
		// Given: tactic 1 has a filled block Claude answered nothing for, tactic 2 left its block empty and
		// was never sent — only the first is a failure worth telling the user about
		List<BreakdownSelection> selections =
				List.of(new BreakdownSelection(1, List.of("dev")), new BreakdownSelection(2, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(
				1, EnumSet.of(BreakdownType.DEVICE),
				2, EnumSet.of(BreakdownType.DEVICE)));
		DeviceTable filled = new DeviceTable("1.20%", "82%", "4", "Mobile", "61%",
				List.of(new DeviceRow("Mobile", "1,200,000", "1.20%", "78%", "$4,000")));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1, 2), "token")).thenReturn(Map.of(
				1, filled,
				2, DeviceTable.EMPTY));
		when(claude.batchDeviceInsights(any(), eq("brief"))).thenReturn(Map.of());

		// When:
		BreakdownValues result = helper.buildDeviceValues(
				"sheet-url", selections,
				Map.of("{{tactic 1}}", "CTV", "{{tactic 2}}", "Display"),
				"brief", "token");

		// Then: exactly one warning, naming the tactic that lost its copy
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Device breakdown", "CTV");
		assertThat(result.warnings().getFirst()).doesNotContain("Display");
	}

	@Test
	void shouldSkipEverythingWhenNoTacticEnabledDeviceBreakdownTest() {
		// Given: the only selection is a different breakdown
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("geo")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.GEO)));

		// When:
		Map<String, String> values =
				helper.buildDeviceValues("sheet-url", selections, Map.of(), "brief", "token").values();

		// Then: the sheet is never read and no values are produced
		assertThat(values).isEmpty();
		verify(sheetHelper, never()).readDeviceTables("sheet-url", Set.of(1), "token");
		verifyNoInteractions(claude);
	}

	@Test
	void shouldCarryTheDecksHeadingOntoTheBreakdownCopyTest() {
		// Given: the deck's placeholder map already resolved tactic 1's heading
		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("dev")));
		when(breakdownResolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.DEVICE)));
		when(sheetHelper.readDeviceTables("sheet-url", Set.of(1), "token"))
				.thenReturn(Map.of(1, DeviceTable.EMPTY));

		// When:
		Map<String, String> values = helper.buildDeviceValues(
				"sheet-url", selections, Map.of("{{tactic 1}}", "CTV"), "brief", "token").values();

		// Then: the heading is re-issued for the copy, which the deck's own placeholder pass can no longer reach
		assertThat(values.get("{{tactic 1}}")).isEqualTo("CTV");
	}
}
