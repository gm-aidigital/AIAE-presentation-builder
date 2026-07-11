package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.DataSourceApi;
import com.aidigital.reportconstructor.api.v1.model.GoogleConnectionStatusV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadRequestV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.api.v1.model.SheetSummaryRequestV1;
import com.aidigital.reportconstructor.api.v1.model.SheetSummaryResultV1;
import com.aidigital.reportconstructor.reports.mappers.DataSourceApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
import com.aidigital.reportconstructor.service.reports.services.SheetSummaryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for Google data-source endpoints: connection status and sheet reads.
 */
@RestController
@RequiredArgsConstructor
public class DataSourceController implements DataSourceApi {

	private final SheetQueryService sheetQuery;
	private final SheetSummaryQueryService sheetSummaryQuery;
	private final DataSourceApiMapper mapper;
	private final AppUserFactory appUserFactory;

	@Override
	public ResponseEntity<GoogleConnectionStatusV1> getGoogleConnectionStatus() {
		var caller = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		return ResponseEntity.ok(mapper.toStatus(sheetQuery.connectionStatus(caller.email())));
	}

	@Override
	public ResponseEntity<SheetReadResultV1> readSheet(SheetReadRequestV1 body) {
		var caller = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		try {
			return ResponseEntity.ok(mapper.toSuccess(
					sheetQuery.fetchTab(body.getUrl(), body.getTab(), caller.userId())));
		} catch (AppException ex) {
			if (ErrorReason.C001.getCode().equals(ex.getCode())
					&& ex.getMessage() != null
					&& ex.getMessage().toLowerCase().contains("not found")) {
				return ResponseEntity.ok(mapper.tabNotFound(body.getTab(), visibleTabsFrom(ex)));
			}
			throw ex;
		}
	}

	/**
	 * Extracts the workbook's visible tab titles that a tab-not-found
	 * {@link AppException} carries in its {@link SheetQueryService#TAB_NOT_FOUND_TABS_PARAM}
	 * structured parameter, so the not-found response can echo them for the client's
	 * manual media-plan tab picker.
	 *
	 * @param ex the caught tab-not-found exception
	 * @return the visible tab titles, or an empty list when none were carried
	 */
	List<String> visibleTabsFrom(AppException ex) {
		return ex.getValidationMessage().getParameters().stream()
				.filter(p -> SheetQueryService.TAB_NOT_FOUND_TABS_PARAM.equals(p.getCode()))
				.findFirst()
				.map(p -> p.getValue().isEmpty()
						? List.<String>of()
						: List.of(p.getValue().split(SheetQueryService.TAB_NOT_FOUND_TABS_DELIMITER)))
				.orElseGet(List::of);
	}

	@Override
	public ResponseEntity<SheetSummaryResultV1> readSheetSummary(SheetSummaryRequestV1 body) {
		var caller = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		return ResponseEntity.ok(mapper.toSummary(
				sheetSummaryQuery.readSummary(body.getSheetUrl(), caller.userId())));
	}
}
