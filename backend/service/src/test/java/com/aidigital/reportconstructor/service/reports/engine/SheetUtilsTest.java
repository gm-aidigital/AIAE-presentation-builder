package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SheetUtilsTest {

	private final SheetRowHelper sheetUtils = ReportsEngineTestSupport.sheetRowHelper();

	@Test
	void findLabelValue_readsCellToTheRight() {
		List<List<String>> rows = List.of(
				List.of("Client name:", "Acme Corp"),
				List.of("Campaign:", "Spring Sale")
		);
		assertThat(sheetUtils.findLabelValue(rows, "Client name:")).isEqualTo("Acme Corp");
		assertThat(sheetUtils.findLabelValue(rows, "missing:")).isNull();
	}

	@Test
	void detectDataDateRange_returnsMinAndMaxOfDateColumn() {
		// Given: a raw-data ("Basic" tab) grid with a delivery header and dated rows
		List<List<String>> rows = List.of(
				List.of("Date", "Channel", "Cost", "Impressions"),
				List.of("2026-03-05", "Display", "10", "1000"),
				List.of("2026-03-01", "Video", "20", "2000"),
				List.of("2026-03-31", "Display", "30", "3000")
		);
		// When: the raw-data date range is detected
		FlightDates fd = sheetUtils.detectDataDateRange(rows);
		// Then: it spans the earliest and latest dates present, ignoring row order
		assertThat(fd).isNotNull();
		assertThat(fd.start()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(fd.end()).isEqualTo(LocalDate.of(2026, 3, 31));
	}

	@Test
	void formatFlightDates_sameYearShowsYearOnce() {
		String formatted = sheetUtils.formatFlightDates(
				LocalDate.of(2026, 2, 12), LocalDate.of(2026, 5, 9));
		assertThat(formatted).contains("2026");
		assertThat(formatted).contains("Feb 12");
		assertThat(formatted).contains("May 9");
	}
}
