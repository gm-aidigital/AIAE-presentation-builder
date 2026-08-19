package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Derives the <em>campaign</em> flight — the whole booked run, which for an EOM report is longer than
 * the reporting window the user selected — and the reporting month's position inside it.
 *
 * <p>Two sources describe the flight and they disagree in practice. The raw-data export shows when the
 * line items were actually live, which stops at the last delivered day and therefore understates a
 * campaign still in flight. The media plan states the booked window per line item, which is what
 * "month 2 of 3" has to be counted against. The media plan therefore sets the boundaries whenever it
 * carries parseable dates, and the raw-data range is the fallback for plans that state no dates at
 * all. The window is finally widened to cover the reporting period, so the reporting month can never
 * fall outside the flight it is numbered within.
 */
@Component
@RequiredArgsConstructor
public class CampaignFlightResolver {

	private final SheetRowHelper sheetRows;
	private final RatePlanCalculator pacing;

	/**
	 * Resolves the campaign flight window from the media plan, falling back to the observed raw-data
	 * range, and widens the result to cover the reporting window.
	 *
	 * @param planRows        media-plan grid scanned for flight-date columns (may be {@code null})
	 * @param dataRange       the full date range present in the raw-data export (may be {@code null})
	 * @param reportingWindow the window the report covers (may be {@code null})
	 * @return the campaign flight window, or {@code null} when no source carries any date
	 */
	public FlightDates resolveCampaignFlight(List<List<String>> planRows, FlightDates dataRange,
	                                         FlightDates reportingWindow) {
		FlightDates plan = mediaPlanWindow(planRows);
		FlightDates base = plan != null ? plan : dataRange;
		if (base == null) {
			return reportingWindow;
		}
		return widen(base, reportingWindow);
	}

	/**
	 * Reads the booked flight window out of the media plan: the earliest start and the latest end
	 * across every line item. Separate start/end columns are preferred; a single column carrying the
	 * whole range in one cell (e.g. {@code "10/1/25 - 12/31/25"}) is read when they are absent.
	 *
	 * @param planRows media-plan grid to scan (may be {@code null})
	 * @return the plan's overall flight window, or {@code null} when the plan states no parseable dates
	 */
	FlightDates mediaPlanWindow(List<List<String>> planRows) {
		if (planRows == null || planRows.isEmpty()) {
			return null;
		}
		List<LocalDate> starts = parseColumn(planRows, MediaPlanColumn.FLIGHT_START.getSynonyms());
		List<LocalDate> ends = parseColumn(planRows, MediaPlanColumn.FLIGHT_END.getSynonyms());
		if (starts.isEmpty() && ends.isEmpty()) {
			List<LocalDate> ranged = parseColumn(planRows, MediaPlanColumn.FLIGHT_RANGE.getSynonyms());
			starts = ranged;
			ends = ranged;
		}
		LocalDate start = min(starts);
		LocalDate end = max(ends);
		if (start == null && end == null) {
			return null;
		}
		return new FlightDates(start != null ? start : end, end != null ? end : start);
	}

	/**
	 * Counts the calendar months the flight spans, e.g. an October–December flight resolves to 3.
	 *
	 * @param flight the campaign flight window (may be {@code null})
	 * @return the month count, or {@code null} when there is no flight window
	 */
	public Integer flightMonthsTotal(FlightDates flight) {
		if (flight == null || flight.start() == null || flight.end() == null) {
			return null;
		}
		return pacing.monthsSpanned(flight.start(), flight.end());
	}

	/**
	 * Numbers the reporting month within the flight: the month the reporting window ends in, counted
	 * from the flight's first calendar month. A November report on an October–December flight is 2.
	 * The result is clamped into {@code [1, flightMonthsTotal]} so a stray date can never produce a
	 * "month 0 of 3" or a "month 4 of 3" cover.
	 *
	 * @param flight          the campaign flight window (may be {@code null})
	 * @param reportingWindow the window the report covers (may be {@code null})
	 * @return the 1-based month index, or {@code null} when either window is missing
	 */
	public Integer flightMonthNumber(FlightDates flight, FlightDates reportingWindow) {
		Integer total = flightMonthsTotal(flight);
		if (total == null || reportingWindow == null || reportingWindow.end() == null) {
			return null;
		}
		int index = pacing.monthsSpanned(flight.start(), reportingWindow.end());
		return Math.min(Math.max(index, 1), total);
	}

	/**
	 * Extends a window so it also covers a second one, leaving it untouched when the second is
	 * {@code null} or already inside.
	 *
	 * @param base  the window to extend
	 * @param cover the window that must fit inside the result (may be {@code null})
	 * @return the covering window
	 */
	FlightDates widen(FlightDates base, FlightDates cover) {
		if (cover == null) {
			return base;
		}
		LocalDate start = min(Arrays.asList(base.start(), cover.start()));
		LocalDate end = max(Arrays.asList(base.end(), cover.end()));
		return new FlightDates(start, end);
	}

	/**
	 * Collects and parses every date found in the media-plan column identified by the given header
	 * synonyms. Cells carrying a range ({@code "10/1/25 - 12/31/25"}) contribute both of their dates.
	 *
	 * @param planRows       media-plan grid to scan
	 * @param headerSynonyms normalised header texts identifying the column
	 * @return the parsed dates in the order the column lists them (never {@code null})
	 */
	List<LocalDate> parseColumn(List<List<String>> planRows, Set<String> headerSynonyms) {
		List<LocalDate> dates = new ArrayList<>();
		for (String value : sheetRows.collectColumnValuesBelow(planRows, headerSynonyms)) {
			for (String part : value.split("\\s(?:-|–|—|to|through|thru)\\s")) {
				LocalDate parsed = sheetRows.parseDate(part.trim());
				if (parsed != null) {
					dates.add(parsed);
				}
			}
		}
		return dates;
	}

	/**
	 * Returns the earliest non-{@code null} date of a list.
	 *
	 * @param dates the dates to compare (entries may be {@code null})
	 * @return the earliest date, or {@code null} when the list holds none
	 */
	LocalDate min(List<LocalDate> dates) {
		return dates.stream().filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
	}

	/**
	 * Returns the latest non-{@code null} date of a list.
	 *
	 * @param dates the dates to compare (entries may be {@code null})
	 * @return the latest date, or {@code null} when the list holds none
	 */
	LocalDate max(List<LocalDate> dates) {
		return dates.stream().filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
	}
}
