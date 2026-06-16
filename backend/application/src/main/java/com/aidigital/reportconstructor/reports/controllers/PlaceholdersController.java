package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.PlaceholdersApi;
import com.aidigital.reportconstructor.api.v1.model.PreviewRequestV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewResultV1;
import com.aidigital.reportconstructor.reports.mappers.PlaceholdersApiMapper;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for placeholder preview endpoints.
 */
@RestController
@RequiredArgsConstructor
public class PlaceholdersController implements PlaceholdersApi {

	private final PlaceholderResolverService placeholders;
	private final PlaceholdersApiMapper mapper;

	@Override
	public ResponseEntity<PreviewResultV1> previewPlaceholders(PreviewRequestV1 body) {
		var result = placeholders.resolve(mapper.toPayload(body));
		return ResponseEntity.ok(mapper.toPreviewResponse(result));
	}
}
