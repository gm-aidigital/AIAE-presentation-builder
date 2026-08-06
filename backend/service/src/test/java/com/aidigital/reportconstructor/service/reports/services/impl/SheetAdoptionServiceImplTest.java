package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownInferenceHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportFileNamer;
import com.aidigital.reportconstructor.service.reports.helpers.ReportJobProgressHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportResumeStateHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetCampaignReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.helpers.SheetTacticCountHelper;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SheetAdoptionServiceImplTest {

	private static final String SHEET = "https://docs.google.com/spreadsheets/d/abc";

	/** Everything the service depends on, so each test can stub just what it is about. */
	private final ReportSheetHelper sheetHelper = mock(ReportSheetHelper.class);
	private final SheetPlaceholderReader placeholderReader = mock(SheetPlaceholderReader.class);
	private final SheetCampaignReader sheetCampaign = mock(SheetCampaignReader.class);
	private final SheetTacticCountHelper tacticCounter = mock(SheetTacticCountHelper.class);
	private final BreakdownInferenceHelper breakdownInference = mock(BreakdownInferenceHelper.class);
	private final ReportJobProgressHelper jobProgress = mock(ReportJobProgressHelper.class);
	private final ReportResumeStateHelper resumeState = mock(ReportResumeStateHelper.class);
	private final ReportFileNamer fileNamer = mock(ReportFileNamer.class);

	/**
	 * Builds the service under test with a token provider that yields no Google token.
	 *
	 * @return the service
	 */
	SheetAdoptionServiceImpl service() {
		@SuppressWarnings("unchecked")
		ObjectProvider<UserGoogleTokenProvider> tokens = mock(ObjectProvider.class);
		when(tokens.getIfAvailable()).thenReturn(null);
		return new SheetAdoptionServiceImpl(sheetHelper, placeholderReader, sheetCampaign, tacticCounter,
				breakdownInference, jobProgress, resumeState, fileNamer, tokens);
	}

	/**
	 * Builds campaign data naming the given tactics in order.
	 *
	 * @param names the tactic names, tactic 1 first
	 * @return the campaign data
	 */
	CampaignData campaign(String... names) {
		Map<Integer, Tactic> tactics = new LinkedHashMap<>();
		for (int i = 0; i < names.length; i++) {
			tactics.put(i + 1, new Tactic(names[i], "display", null, 0, 0, 0, 0,
					null, null, null, null, null, null, null, null, null, null, null, null));
		}
		return new CampaignData("Acme", "Summer", "US", "Awareness", "1 Jul – 31 Jul", null,
				"$100,000", "CTR", String.join(", ", names), "25-54", "Auto", null, tactics, null);
	}

	/**
	 * Stubs a readable workbook reporting the given tactics.
	 *
	 * @param values the placeholder values the workbook carries
	 * @param names  the tactic names it reports
	 */
	void stubWorkbook(Map<String, String> values, String... names) {
		when(sheetHelper.readSheetGrid(eq(SHEET), eq(null))).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(List.of(List.of("cell")))).thenReturn(values);
		when(tacticCounter.countFromPlaceholders(values)).thenReturn(names.length);
		when(sheetCampaign.read(values, names.length)).thenReturn(campaign(names));
	}

	@Test
	void shouldRegisterTheWorkbookAsAFinishedSheetBuildTest() {
		// Given: a readable report workbook with two tactics
		Map<String, String> values = new LinkedHashMap<>();
		values.put("{{RFP info}}", "the campaign context");
		values.put("{{change log}}", "budget shifted in week 3");
		values.put("{{market volume}}", "1.2M");
		stubWorkbook(values, "Display", "CTV");
		when(breakdownInference.infer(SHEET, 2, null))
				.thenReturn(List.of(new BreakdownSelection(1, List.of("aud"))));
		when(fileNamer.buildFileName("EOC", "Acme", "a@b.com")).thenReturn("EOC_Acme_report");
		when(resumeState.serialize(org.mockito.ArgumentMatchers.any(ReportResumeState.class)))
				.thenReturn("{\"reportType\":\"EOC\"}");
		ReportJobEntity job = new ReportJobEntity();
		job.setId(7L);
		when(jobProgress.createQueuedJob("user-1", "EOC")).thenReturn(job);

		// When: the sheet is adopted
		ReportResume resume = service().adopt("user-1", "clerk-1", "a@b.com", SHEET, "EOC");

		// Then: the job is stamped as a done SHEET build pointing at the user's own workbook
		verify(jobProgress).recordJobContext(7L, "a@b.com", GenerationTarget.SHEET.name(), null, null);
		verify(jobProgress).recordResumeState(7L, "{\"reportType\":\"EOC\"}");
		verify(jobProgress).recordArtifact(7L, "EOC_Acme_report", SHEET);
		verify(jobProgress).markJobDone(7L, SHEET, null);
		assertThat(resume.jobId()).isEqualTo(7L);
		assertThat(resume.sheetUrl()).isEqualTo(SHEET);
	}

	@Test
	void shouldTakeTheBriefAndTheBreakdownsFromTheWorkbookTest() {
		// Given: a workbook carrying its own campaign context and a filled audience section
		Map<String, String> values = new LinkedHashMap<>();
		values.put("{{RFP info}}", "the campaign context");
		values.put("{{change log}}", "budget shifted in week 3");
		values.put("{{market volume}}", "1.2M");
		stubWorkbook(values, "Display", "CTV");
		List<BreakdownSelection> inferred = List.of(new BreakdownSelection(1, List.of("aud")));
		when(breakdownInference.infer(SHEET, 2, null)).thenReturn(inferred);
		ReportJobEntity job = new ReportJobEntity();
		job.setId(7L);
		when(jobProgress.createQueuedJob("user-1", "EOC")).thenReturn(job);

		// When
		ReportResume resume = service().adopt("user-1", "clerk-1", "a@b.com", SHEET, "EOC");

		// Then: the state is built from the sheet, with no invented date window
		ArgumentCaptor<ReportResumeState> stored = ArgumentCaptor.forClass(ReportResumeState.class);
		verify(resumeState).serialize(stored.capture());
		ReportResumeState state = stored.getValue();
		assertThat(state.brief()).isEqualTo("the campaign context");
		assertThat(state.changeLog()).isEqualTo("budget shifted in week 3");
		assertThat(state.marketVolume()).isEqualTo("1.2M");
		assertThat(state.breakdownSelections()).isSameAs(inferred);
		assertThat(state.tacticNames()).containsExactly("Display", "CTV");
		assertThat(state.dateFilter()).isNull();
		assertThat(resume.state()).isSameAs(state);
	}

	@Test
	void shouldRejectASheetThatCannotBeReadTest() {
		// Given: a link whose spreadsheet id does not resolve, so the grid comes back empty
		when(sheetHelper.readSheetGrid(eq(SHEET), eq(null))).thenReturn(List.of());

		// When-Then: the user is told before any job row exists
		assertThatThrownBy(() -> service().adopt("user-1", "clerk-1", "a@b.com", SHEET, "EOC"))
				.isInstanceOf(AppException.class)
				.hasMessageContaining("Could not read");
		verify(jobProgress, never()).createQueuedJob("user-1", "EOC");
	}

	@Test
	void shouldRejectASpreadsheetThatIsNotAReportWorkbookTest() {
		// Given: a readable spreadsheet that names no tactics
		Map<String, String> values = Map.of("{{client_name}}", "Acme");
		when(sheetHelper.readSheetGrid(eq(SHEET), eq(null))).thenReturn(List.of(List.of("cell")));
		when(placeholderReader.readPlaceholders(List.of(List.of("cell")))).thenReturn(values);
		when(tacticCounter.countFromPlaceholders(values)).thenReturn(0);

		// When-Then: rejected up front rather than three Claude batches later
		assertThatThrownBy(() -> service().adopt("user-1", "clerk-1", "a@b.com", SHEET, "EOC"))
				.isInstanceOf(AppException.class)
				.hasMessageContaining("no tactics found");
		verify(jobProgress, never()).createQueuedJob("user-1", "EOC");
	}

	@Test
	void shouldRejectABlankSheetUrlTest() {
		// When-Then: nothing is read and nothing is written
		assertThatThrownBy(() -> service().adopt("user-1", "clerk-1", "a@b.com", "  ", "EOC"))
				.isInstanceOf(AppException.class)
				.hasMessageContaining("Sheet URL is required");
		verify(sheetHelper, never()).readSheetGrid("  ", null);
	}
}
