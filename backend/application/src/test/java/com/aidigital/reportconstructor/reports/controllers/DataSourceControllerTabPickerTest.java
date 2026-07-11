package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.reports.mappers.DataSourceApiMapperImpl;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.common.error.ValidationParameter;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the tab-not-found path that powers the manual media-plan tab
 * picker: the mapper echoing the workbook's visible tabs, and the controller
 * extracting them from the structured exception parameter.
 */
class DataSourceControllerTabPickerTest {

	@Test
	void shouldEchoVisibleTabsInTabNotFoundResultTest() {
		// Given: the workbook's visible tabs and the missing requested tab
		DataSourceApiMapperImpl mapper = new DataSourceApiMapperImpl();
		List<String> tabs = List.of("Overview", "Plan v2", "Basic");

		// When
		SheetReadResultV1 result = mapper.tabNotFound("Proposal", tabs);

		// Then: not ok, coded tab_not_found, and the visible tabs are carried back
		assertThat(result.getOk()).isFalse();
		assertThat(result.getError()).isEqualTo("tab_not_found");
		assertThat(result.getTab()).isEqualTo("Proposal");
		assertThat(result.getTabs()).containsExactly("Overview", "Plan v2", "Basic");
	}

	@Test
	void shouldExtractVisibleTabsFromExceptionParameterTest() {
		// Given: a C001 exception carrying the visible tabs as a delimited parameter
		DataSourceController controller = new DataSourceController(null, null, null, null);
		AppException ex = new AppException(ErrorReason.C001,
				new ValidationParameter("param0", "Tab \"Proposal\" not found"),
				new ValidationParameter(SheetQueryService.TAB_NOT_FOUND_TABS_PARAM,
						String.join(SheetQueryService.TAB_NOT_FOUND_TABS_DELIMITER,
								List.of("Overview", "Plan v2"))));

		// When
		List<String> tabs = controller.visibleTabsFrom(ex);

		// Then
		assertThat(tabs).containsExactly("Overview", "Plan v2");
	}

	@Test
	void shouldReturnEmptyWhenExceptionCarriesNoVisibleTabsTest() {
		// Given: a C001 exception without the visible-tabs parameter
		DataSourceController controller = new DataSourceController(null, null, null, null);
		AppException ex = new AppException(ErrorReason.C001, "Tab \"Geo\" not found");

		// When
		List<String> tabs = controller.visibleTabsFrom(ex);

		// Then
		assertThat(tabs).isEmpty();
	}

	@Test
	void shouldReturnEmptyWhenVisibleTabsParameterIsBlankTest() {
		// Given: the visible-tabs parameter is present but empty (workbook has no visible tabs)
		DataSourceController controller = new DataSourceController(null, null, null, null);
		AppException ex = new AppException(ErrorReason.C001,
				new ValidationParameter(SheetQueryService.TAB_NOT_FOUND_TABS_PARAM, ""));

		// When
		List<String> tabs = controller.visibleTabsFrom(ex);

		// Then
		assertThat(tabs).isEmpty();
	}
}
