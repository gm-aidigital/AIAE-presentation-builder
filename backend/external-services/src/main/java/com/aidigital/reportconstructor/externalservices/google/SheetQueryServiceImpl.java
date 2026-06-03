package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.SheetQueryService;
import com.aidigital.reportconstructor.service.reports.dto.GoogleConnectionStatus;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.aidigital.reportconstructor.usagelogging.LogUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Default {@link SheetQueryService} implementation backed by the active
 * {@link GoogleSheetsProvider} (real or stub). Encapsulates the
 * connection-status assembly that previously lived inline in the sheets
 * controller.
 */
@Service
@RequiredArgsConstructor
public class SheetQueryServiceImpl implements SheetQueryService {

    private final GoogleSheetsProvider sheets;

    @Override
    @LogUsage
    public GoogleConnectionStatus connectionStatus(String callerEmail) {
        return new GoogleConnectionStatus(true, !sheets.isLive(), callerEmail, java.util.List.of(), "");
    }

    @Override
    @LogUsage
    public SheetData fetchTab(String spreadsheetUrl, String tab) {
        return sheets.fetchTab(spreadsheetUrl, tab);
    }
}
