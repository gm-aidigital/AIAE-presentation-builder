package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.SheetData;

/**
 * Abstraction over Google Sheets fetches. {@link RealGoogleSheetsProvider}
 * calls the real Sheets v4 API with service-account credentials; the
 * {@link StubGoogleSheetsProvider} returns a deterministic fixture for
 * offline demos. Bean selection is automatic — see {@code RealGoogleSheetsProvider}.
 */
public interface GoogleSheetsProvider {

    /** @return true when the provider is talking to the real Google APIs. */
    boolean isLive();

    /**
     * Reads the requested tab from the spreadsheet at the given URL.
     *
     * @param spreadsheetUrl Google Sheets URL
     * @param tab            tab name (Proposal, Workspace, Basic, …)
     * @return sheet payload mirroring the PHP {@code fetch_sheet.php} shape
     */
    SheetData fetchTab(String spreadsheetUrl, String tab);
}
