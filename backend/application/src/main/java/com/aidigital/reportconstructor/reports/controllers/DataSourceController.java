package com.aidigital.reportconstructor.reports.controllers;

import com.aidigital.reportconstructor.api.v1.DataSourceApi;
import com.aidigital.reportconstructor.api.v1.model.GoogleConnectionStatusV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadRequestV1;
import com.aidigital.reportconstructor.api.v1.model.SheetReadResultV1;
import com.aidigital.reportconstructor.reports.mappers.DataSourceApiMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.SheetQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DataSourceController implements DataSourceApi {

    private final SheetQueryService sheetQuery;
    private final DataSourceApiMapper mapper;
    private final AppUserFactory appUserFactory;

    @Override
    public ResponseEntity<GoogleConnectionStatusV1> getGoogleConnectionStatus() {
        var caller = appUserFactory.from(SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok(mapper.toStatus(sheetQuery.connectionStatus(caller.email())));
    }

    @Override
    public ResponseEntity<SheetReadResultV1> readSheet(SheetReadRequestV1 body) {
        try {
            return ResponseEntity.ok(mapper.toSuccess(
                sheetQuery.fetchTab(body.getUrl(), body.getTab())));
        } catch (AppException ex) {
            if (ErrorReason.C001.getCode().equals(ex.getCode())
                && ex.getMessage() != null
                && ex.getMessage().toLowerCase().contains("not found")) {
                return ResponseEntity.ok(mapper.tabNotFound(body.getTab()));
            }
            throw ex;
        }
    }
}
