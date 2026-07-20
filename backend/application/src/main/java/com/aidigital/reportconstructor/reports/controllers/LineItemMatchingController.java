package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.LineItemMatchingApi;
import com.aidigital.reportconstructor.api.v1.model.LineItemMatchRequestV1;
import com.aidigital.reportconstructor.api.v1.model.LineItemMatchResultV1;
import com.aidigital.reportconstructor.reports.mappers.LineItemMatchResultMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.reports.services.LineItemMatcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the line-item matching endpoint (Media-Plan tactics &rarr; BigQuery line items).
 */
@RestController
@RequiredArgsConstructor
public class LineItemMatchingController implements LineItemMatchingApi {

	private final LineItemMatcherService matcher;
	private final LineItemMatchResultMapper mapper;
	private final AppUserFactory appUserFactory;

	@Override
	public ResponseEntity<LineItemMatchResultV1> matchLineItems(LineItemMatchRequestV1 body) {
		var user = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
		var result = matcher.match(body.getBqRows(), body.getPlanRows(), user);
		return ResponseEntity.ok(mapper.toResult(result));
	}
}
