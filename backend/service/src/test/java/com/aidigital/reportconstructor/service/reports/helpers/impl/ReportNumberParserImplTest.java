package com.aidigital.reportconstructor.service.reports.helpers.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportNumberParserImplTest {

	private final ReportNumberParserImpl parser = new ReportNumberParserImpl();

	@Test
	void parseReportNumber_stripsCommasAndNonNumeric() {
		assertThat(parser.parseReportNumber("1,234.5")).isEqualTo(1234.5);
		assertThat(parser.parseReportNumber("$1,234")).isEqualTo(1234.0);
		assertThat(parser.parseReportNumber("n/a")).isZero();
		assertThat(parser.parseReportNumber(null)).isZero();
	}

	@Test
	void parseReportNumber_expandsCompactMagnitudeSuffixes() {
		// Given: values the generated sheet stores compactly via Fmt.compact
		// When-Then: the k/M/B suffix is expanded to the true magnitude instead of being dropped
		assertThat(parser.parseReportNumber("74k")).isEqualTo(74_000.0);
		assertThat(parser.parseReportNumber("1.2M")).isEqualTo(1_200_000.0);
		assertThat(parser.parseReportNumber("3B")).isEqualTo(3_000_000_000.0);
		assertThat(parser.parseReportNumber("3.45")).isEqualTo(3.45);
	}
}
