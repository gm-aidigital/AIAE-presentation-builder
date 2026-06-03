package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.PlaceholdersApi;
import com.aidigital.reportconstructor.api.v1.model.PreviewRequestV1;
import com.aidigital.reportconstructor.api.v1.model.PreviewResultV1;
import com.aidigital.reportconstructor.reports.mappers.PlaceholdersApiMapper;
import com.aidigital.reportconstructor.service.reports.PlaceholderResolverService;
import com.aidigital.reportconstructor.usagelogging.LogUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlaceholdersController implements PlaceholdersApi {

    private final PlaceholderResolverService placeholders;
    private final PlaceholdersApiMapper mapper;

    @Override
    @LogUsage(action = "placeholders.preview")
    public ResponseEntity<PreviewResultV1> previewPlaceholders(PreviewRequestV1 body) {
        var result = placeholders.resolve(mapper.toPayload(body));
        return ResponseEntity.ok(mapper.toPreviewResponse(result));
    }
}
