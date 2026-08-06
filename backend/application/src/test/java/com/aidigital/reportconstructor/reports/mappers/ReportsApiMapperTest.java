package com.aidigital.reportconstructor.reports.mappers;

import com.aidigital.reportconstructor.api.v1.model.DateFilterModeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportResumeV1;
import com.aidigital.reportconstructor.api.v1.model.ReportSummaryV1;
import com.aidigital.reportconstructor.api.v1.model.ReportTypeV1;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.DateFilter;
import com.aidigital.reportconstructor.service.reports.dto.DateFilterMode;
import com.aidigital.reportconstructor.service.reports.dto.ReportResume;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.dto.ReportSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportsApiMapperTest {

	private final ReportsApiMapperImpl mapper = new ReportsApiMapperImpl();

	@Test
	void shouldFlattenTheStoredStateOntoTheResumeResponseTest() {
		// Given: a draft whose stored state carries a window, toggles and tactic names
		ReportResume resume = new ReportResume(7L, "https://sheet/7", "https://plan", "https://elevate",
				new ReportResumeState("EOM", "the brief", "the log", "1.2M",
						new DateFilter(DateFilterMode.RANGE, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
						Boolean.FALSE, List.of(new BreakdownSelection(2, List.of("aud", "dev"))),
						List.of("Display", "CTV")));

		// When: it is mapped to the API DTO
		ReportResumeV1 dto = mapper.toResume(resume);

		// Then: every nested field lands on the flat response
		assertThat(dto.getJobId()).isEqualTo(7L);
		assertThat(dto.getSheetUrl()).isEqualTo("https://sheet/7");
		assertThat(dto.getMediaPlanUrl()).isEqualTo("https://plan");
		assertThat(dto.getElevateUrl()).isEqualTo("https://elevate");
		assertThat(dto.getReportType()).isEqualTo(ReportTypeV1.EOM);
		assertThat(dto.getBrief()).isEqualTo("the brief");
		assertThat(dto.getChangeLog()).isEqualTo("the log");
		assertThat(dto.getMarketVolume()).isEqualTo("1.2M");
		assertThat(dto.getEstimateDaypartGender()).isFalse();
		assertThat(dto.getDateFilter().getMode()).isEqualTo(DateFilterModeV1.RANGE);
		assertThat(dto.getDateFilter().getEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(dto.getBreakdownSelections()).singleElement()
				.satisfies(sel -> {
					assertThat(sel.getTacticNum()).isEqualTo(2);
					assertThat(sel.getBreakdowns()).containsExactly("aud", "dev");
				});
		assertThat(dto.getTacticNames()).containsExactly("Display", "CTV");
	}

	@Test
	void shouldLeaveTheReportTypeUnsetWhenTheStoredCodeIsMissingOrUnknownTest() {
		// Given: drafts stored before the type was recorded, and one carrying a retired code

		// When-Then: neither blows up on the enum — the client falls back to asking the user
		assertThat(mapper.toReportType(null)).isNull();
		assertThat(mapper.toReportType("  ")).isNull();
		assertThat(mapper.toReportType("QBR")).isNull();
		assertThat(mapper.toReportType("EOC")).isEqualTo(ReportTypeV1.EOC);
	}

	@Test
	void shouldCarryTheDraftFlagOntoTheHistoryRowTest() {
		// Given: a history row for a resumable sheet build
		ReportSummary summary = new ReportSummary(1L, "EOC", "done", "EOC report", null, null,
				"https://sheet/1", null, null, null, null, null, 0, 0, 0, 0d, true);

		// When: it is mapped to the API DTO
		ReportSummaryV1 dto = mapper.toSummary(summary);

		// Then: the client can tell a draft from a finished report
		assertThat(dto.getDraft()).isTrue();
		assertThat(dto.getSheetUrl()).isEqualTo("https://sheet/1");
		assertThat(dto.getSlideUrl()).isNull();
	}
}
