package com.aidigital.reportconstructor.service.reports.helpers.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportNumberParserImplTest {

	private final ReportNumberParserImpl parser = new ReportNumberParserImpl();

	@Test
	void parseReportNumber_stripsCommasAndNonNumeric() {
		assertThat(parser.parseReportNumber("1,234.5")).isEqualTo(1234.5);
		assertThat(parser.parseReportNumber("n/a")).isZero();
		assertThat(parser.parseReportNumber(null)).isZero();
	}
}
