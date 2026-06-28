package com.aidigital.reportconstructor.service.reports.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FmtTest {

	private final Fmt fmt = new Fmt();

	@Test
	void intGroup_roundsAndGroups() {
		assertEquals("1,234,568", fmt.intGroup(1_234_567.8));
	}

	@Test
	void money_prefixesDollar() {
		assertEquals("$1,000", fmt.money(999.6));
	}

	@Test
	void pctOrDash_zeroIsEmDash() {
		assertEquals("\u2014", fmt.pctOrDash(0));
		assertEquals("12.34%", fmt.pctOrDash(12.34));
	}

	@Test
	void pct1_oneDecimalPercent() {
		assertEquals("2.5%", fmt.pct1(2.53));
		assertEquals("95.7%", fmt.pct1(95.7));
		assertEquals("0.0%", fmt.pct1(0));
	}

	@Test
	void compact_thousandsTruncateToWholeK() {
		assertEquals("74k", fmt.compact(74_542));
		assertEquals("702k", fmt.compact(702_431));
	}

	@Test
	void compact_millionsTruncateToOneDecimalM() {
		assertEquals("1.2M", fmt.compact(1_234_567));
	}

	@Test
	void compact_belowThousandIsGroupedInteger() {
		assertEquals("742", fmt.compact(742));
	}
}
