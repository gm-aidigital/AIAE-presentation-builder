package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.model.Request;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RealSheetDeckProviderTest {

	private RealSheetDeckProvider newProvider() {
		GoogleCredentialsFactory creds = Mockito.mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(new NetHttpTransport());
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> {
		});
		GoogleProperties props = Mockito.mock(GoogleProperties.class);
		when(props.getSheetsTemplateId()).thenReturn("template");
		when(props.getSheetsTargetFolderId()).thenReturn("");
		SheetPacingTableWriter writer = Mockito.mock(SheetPacingTableWriter.class);
		GoogleRequestRetrier retrier = Mockito.mock(GoogleRequestRetrier.class);
		return new RealSheetDeckProvider(creds, props, writer, retrier);
	}

	@Test
	void summaryRowDashRequests_dashesUnusedSlotsAndLeavesTotalsRowInPlaceTest() {
		// Given: a summary table whose header sits at row index 2, so its 28 tactic slots occupy rows 3..30,
		// and only the first three tactics are active
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = new ArrayList<>();
		grid.add(List.of("intro"));
		grid.add(List.of("more"));
		grid.add(List.of("Tactic name", "Benchmark", "KPI type", "Imps Plan"));
		int sheetId = 7;
		int tacticCount = 3;

		// When:
		List<Request> requests = provider.summaryRowDashRequests(grid, sheetId, tacticCount);

		// Then: one dash-fill per unused slot (28 - 3 = 25), the first covering row index 2+4 across the
		// table's four columns, writing an em-dash without touching formatting — and no totals row is moved
		assertThat(requests).hasSize(25);
		Request first = requests.get(0);
		assertThat(first.getRepeatCell()).isNotNull();
		assertThat(first.getRepeatCell().getFields()).isEqualTo("userEnteredValue");
		assertThat(first.getRepeatCell().getRange().getStartRowIndex()).isEqualTo(6);
		assertThat(first.getRepeatCell().getRange().getEndRowIndex()).isEqualTo(7);
		assertThat(first.getRepeatCell().getRange().getStartColumnIndex()).isEqualTo(0);
		assertThat(first.getRepeatCell().getRange().getEndColumnIndex()).isEqualTo(4);
		assertThat(first.getRepeatCell().getCell().getUserEnteredValue().getStringValue()).isEqualTo("—");
		assertThat(requests).noneMatch(r -> r.getCopyPaste() != null);
	}

	@Test
	void summaryRowDashRequests_noRequestsWhenHeaderMissingTest() {
		// Given: a grid with no summary-table header row
		RealSheetDeckProvider provider = newProvider();
		List<List<String>> grid = List.of(List.of("nothing", "here"));

		// When:
		List<Request> requests = provider.summaryRowDashRequests(grid, 1, 3);

		// Then: the dash-fill is skipped rather than guessing a location
		assertThat(requests).isEmpty();
	}
}
