package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.dto.FlightDates;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignFlightResolverTest {

	@Test
	void shouldTakeFlightBoundsFromMediaPlanStartAndEndColumnsTest() {
		// Given: a plan whose line items run 10/1 – 12/31 while delivery data stops mid-November
		CampaignFlightResolver resolver = ReportsEngineTestSupport.campaignFlightResolver();
		List<List<String>> plan = List.of(
				List.of("Media", "Start Date", "End Date"),
				List.of("CTV", "10/01/2025", "11/30/2025"),
				List.of("Display", "10/15/2025", "12/31/2025"));
		FlightDates dataRange = new FlightDates(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 11, 14));
		FlightDates reporting = new FlightDates(LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30));

		// When:
		FlightDates flight = resolver.resolveCampaignFlight(plan, dataRange, reporting);

		// Then: the plan's widest window wins over the shorter observed range
		assertThat(flight).isEqualTo(new FlightDates(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)));
		assertThat(resolver.flightMonthsTotal(flight)).isEqualTo(3);
		assertThat(resolver.flightMonthNumber(flight, reporting)).isEqualTo(2);
	}

	@Test
	void shouldReadASingleFlightColumnCarryingBothDatesTest() {
		// Given: a plan stating each line item's flight as one "start - end" cell
		CampaignFlightResolver resolver = ReportsEngineTestSupport.campaignFlightResolver();
		List<List<String>> plan = List.of(
				List.of("Media", "Flight Dates"),
				List.of("CTV", "10/01/2025 - 11/30/2025"),
				List.of("Display", "11/01/2025 - 12/31/2025"));

		// When:
		FlightDates flight = resolver.mediaPlanWindow(plan);

		// Then:
		assertThat(flight).isEqualTo(new FlightDates(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)));
	}

	@Test
	void shouldFallBackToTheObservedDataRangeWhenThePlanStatesNoDatesTest() {
		// Given: a plan with no date columns at all
		CampaignFlightResolver resolver = ReportsEngineTestSupport.campaignFlightResolver();
		List<List<String>> plan = List.of(
				List.of("Media", "Budget"),
				List.of("CTV", "$10,000"));
		FlightDates dataRange = new FlightDates(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 8, 31));

		// When:
		FlightDates flight = resolver.resolveCampaignFlight(plan, dataRange, null);

		// Then:
		assertThat(flight).isEqualTo(dataRange);
		assertThat(resolver.flightMonthsTotal(flight)).isEqualTo(2);
	}

	@Test
	void shouldWidenTheFlightSoTheReportingWindowAlwaysFitsInsideItTest() {
		// Given: a reporting month running past the plan's stated end (a flight extension)
		CampaignFlightResolver resolver = ReportsEngineTestSupport.campaignFlightResolver();
		List<List<String>> plan = List.of(
				List.of("Media", "Start Date", "End Date"),
				List.of("CTV", "10/01/2025", "11/30/2025"));
		FlightDates reporting = new FlightDates(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31));

		// When:
		FlightDates flight = resolver.resolveCampaignFlight(plan, null, reporting);

		// Then: the flight stretches to December, and the report is its third month
		assertThat(flight.end()).isEqualTo(LocalDate.of(2025, 12, 31));
		assertThat(resolver.flightMonthNumber(flight, reporting)).isEqualTo(3);
	}

	@Test
	void shouldClampTheMonthNumberIntoTheFlightTest() {
		// Given: a reporting window that starts before the flight does
		CampaignFlightResolver resolver = ReportsEngineTestSupport.campaignFlightResolver();
		FlightDates flight = new FlightDates(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31));
		FlightDates reporting = new FlightDates(LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 31));

		// When:
		Integer monthNumber = resolver.flightMonthNumber(flight, reporting);

		// Then:
		assertThat(monthNumber).isEqualTo(1);
	}

	@Test
	void shouldReturnNoFlightWhenNoSourceCarriesADateTest() {
		// Given: no plan dates, no data range and no reporting window
		CampaignFlightResolver resolver = ReportsEngineTestSupport.campaignFlightResolver();

		// When:
		FlightDates flight = resolver.resolveCampaignFlight(List.of(), null, null);

		// Then:
		assertThat(flight).isNull();
		assertThat(resolver.flightMonthsTotal(null)).isNull();
		assertThat(resolver.flightMonthNumber(null, null)).isNull();
	}
}
