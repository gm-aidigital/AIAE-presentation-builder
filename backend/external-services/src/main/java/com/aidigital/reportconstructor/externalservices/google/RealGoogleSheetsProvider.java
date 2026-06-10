package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real Google Sheets v4 implementation. Activated automatically when
 * {@link GoogleCredentialsFactory} is on the context (i.e. when
 * {@code GOOGLE_SERVICE_ACCOUNT_JSON} is set). When this bean is absent the
 * {@code StubGoogleSheetsProvider} in {@code service} wins via
 * {@code @ConditionalOnMissingBean}.
 */
@Slf4j
@Component
@Primary
@ConditionalOnBean(GoogleCredentialsFactory.class)
public class RealGoogleSheetsProvider implements GoogleSheetsProvider {

    private static final Pattern ID_PATTERN = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9-_]+)");
    private static final String APPLICATION_NAME = "Report Constructor — AI Digital";

    private final Sheets client;

    public RealGoogleSheetsProvider(GoogleCredentialsFactory creds) {
        this.client = new Sheets.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public SheetData fetchTab(String spreadsheetUrl, String tab) {
        if (!StringUtils.hasText(spreadsheetUrl)) {
            throw new AppException(ErrorReason.C002, "Spreadsheet URL is required");
        }
        Matcher m = ID_PATTERN.matcher(spreadsheetUrl);
        if (!m.find()) {
            throw new AppException(ErrorReason.C002,
                "Could not parse Google Sheets URL. Expected /spreadsheets/d/<id>/…");
        }
        String sheetId = m.group(1);
        try {
            Spreadsheet meta = client.spreadsheets().get(sheetId)
                .setFields("properties.title,sheets.properties.title")
                .execute();
            String title = meta.getProperties() == null ? "Untitled" : meta.getProperties().getTitle();
            List<String> tabs = new ArrayList<>();
            for (Sheet s : meta.getSheets()) {
                if (s.getProperties() != null && s.getProperties().getTitle() != null) {
                    tabs.add(s.getProperties().getTitle());
                }
            }

            // Mirror PHP fetch_sheet.php: verify the tab exists before reading its
            // range. Optional tabs (e.g. "Geo") are often absent, and Google
            // reports a missing sheet as a confusing "Unable to parse range" 400.
            // Surface a clean 404 instead — the frontend already treats these
            // optional fetches as best-effort (.catch(() => null)).
            if (!tabs.contains(tab)) {
                throw new AppException(ErrorReason.C001,
                    "Tab \"" + tab + "\" not found. Available tabs: " + String.join(", ", tabs));
            }

            String range = "'" + tab.replace("'", "''") + "'!A1:ZZ";
            ValueRange vr = client.spreadsheets().values().get(sheetId, range).execute();
            List<List<Object>> values = vr.getValues() == null ? List.of() : vr.getValues();
            List<List<String>> rows = new ArrayList<>(values.size());
            for (List<Object> r : values) {
                List<String> row = new ArrayList<>(r.size());
                for (Object cell : r) {
                    row.add(cell == null ? "" : cell.toString());
                }
                rows.add(row);
            }
            List<String> headers = rows.isEmpty() ? List.of() : rows.get(0);
            return new SheetData(
                sheetId, title, tab, tabs,
                rows.size(), headers.size(), headers,
                rows.stream().limit(5).toList(),
                rows
            );
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
            int code = ex.getStatusCode();
            String googleMsg = ex.getDetails() != null && ex.getDetails().getMessage() != null
                ? ex.getDetails().getMessage()
                : ex.getStatusMessage();
            log.error("[sheets] fetch failed for {} tab={} (HTTP {})", sheetId, tab, code, ex);
            String hint = (code == 403 || code == 404)
                ? "The report's Google service account can't open this spreadsheet — share it with the service account (Viewer access), or check the link"
                : "Google Sheets request failed";
            throw new AppException(ErrorReason.C000,
                hint + (googleMsg != null && !googleMsg.isBlank() ? " (" + googleMsg + ")" : "") + " [HTTP " + code + "]");
        } catch (IOException ex) {
            log.error("[sheets] fetch failed for {} tab={}", sheetId, tab, ex);
            throw new AppException(ErrorReason.C000, "Google Sheets fetch failed: " + ex.getMessage());
        }
    }
}
