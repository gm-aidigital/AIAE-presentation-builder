package com.aidigital.reportconstructor.service.reports.helpers.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineItemNamingHelperImplTest {

	private final LineItemNamingHelperImpl helper = new LineItemNamingHelperImpl();

	@Test
	void extractLineItemId_readsIndexEight() {
		assertThat(helper.extractLineItemId("a_b_c_d_e_f_g_h_42_tail")).isEqualTo("42");
		assertThat(helper.extractLineItemId("short_name")).isNull();
		assertThat(helper.extractLineItemId("a_b_c_d_e_f_g_h_-_tail")).isNull();
	}

	@Test
	void extractLineItemIdOrBlank_returnsEmptyWhenInvalid() {
		assertThat(helper.extractLineItemIdOrBlank("a_b_c_d_e_f_g_h_12345_x")).isEqualTo("12345");
		assertThat(helper.extractLineItemIdOrBlank("a_b_c")).isEmpty();
		assertThat(helper.extractLineItemIdOrBlank(null)).isEmpty();
	}
}
