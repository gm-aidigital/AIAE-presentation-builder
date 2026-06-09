package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.SheetQueryService;
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

    /**
     * Assembles the Google connection status for the sheets UI, reporting whether the
     * backing provider is a stub (offline) and echoing the requesting account.
     *
     * @param callerEmail email of the account whose connection is being reported
     * @return status flagged as connected, marked stub when the provider is not live,
     *         carrying the caller email and empty spreadsheet list/error fields
     */
    @Override
    @LogUsage
    public GoogleConnectionStatus connectionStatus(String callerEmail) {
        return new GoogleConnectionStatus(true, !sheets.isLive(), callerEmail, java.util.List.of(), "");
    }

    /**
     * Reads a single tab of a Google spreadsheet through the active provider.
     *
     * @param spreadsheetUrl full URL of the target Google spreadsheet
     * @param tab            name of the worksheet tab to read within that spreadsheet
     * @return the tab's cell contents wrapped as {@link SheetData}
     */
    @Override
    @LogUsage
    public SheetData fetchTab(String spreadsheetUrl, String tab) {
        return sheets.fetchTab(spreadsheetUrl, tab);
    }
}
