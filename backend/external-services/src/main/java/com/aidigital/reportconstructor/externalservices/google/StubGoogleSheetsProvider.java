package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic mock provider used when no {@code @Primary} real Google
 * Sheets bean is registered (i.e. when {@code GOOGLE_SERVICE_ACCOUNT_JSON}
 * is unset and {@link RealGoogleSheetsProvider} stays conditional-excluded).
 *
 * <p>Returns fixture rows tailored to the requested tab so the downstream
 * matcher and placeholder resolvers see realistic shapes.
 */
@Component
public class StubGoogleSheetsProvider implements GoogleSheetsProvider {

    private static final Pattern ID_PATTERN = Pattern.compile("/spreadsheets/d/([a-zA-Z0-9-_]+)");

    @Override
    public boolean isLive() {
        return false;
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
        List<String> tabs = List.of("Proposal", "Audience&Inventory", "Estimates", "Geo", "Basic", "Workspace");
        List<List<String>> rows = sampleRowsFor(tab);
        List<String> headers = rows.isEmpty() ? List.of() : rows.get(0);
        int cols = headers.size();
        return new SheetData(
            sheetId,
            "Sample Media Plan — Stub",
            tab,
            tabs,
            rows.size(),
            cols,
            headers,
            rows.stream().limit(5).toList(),
            rows
        );
    }

    private List<List<String>> sampleRowsFor(String tab) {
        return switch (tab) {
            case "Basic", "Workspace" -> bigQueryFixture();
            case "Audience&Inventory" -> audienceFixture();
            case "Estimates" -> estimatesFixture();
            case "Geo" -> geoFixture();
            default -> proposalFixture();
        };
    }

    private List<List<String>> proposalFixture() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Client", "Campaign", "Flight", "Budget", "KPI"));
        rows.add(List.of("Demo Client", "Awareness Q2", "May 1 – Jun 30", "$120,000", "Impressions"));
        rows.add(List.of("Media", "Tactic", "Cost", "Comments", ""));
        rows.add(List.of("CTV", "Premium CTV", "$45,000", "Roku + Hulu", ""));
        rows.add(List.of("Display", "Programmatic Display", "$30,000", "Open exchange", ""));
        rows.add(List.of("Video", "Online Video", "$25,000", "YouTube + Trade Desk", ""));
        rows.add(List.of("Audio", "Streaming Audio", "$20,000", "Spotify + iHeart", ""));
        return rows;
    }

    private List<List<String>> bigQueryFixture() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Level 1 Naming", "Tactic", "Channel", "Impressions", "Clicks", "Spend"));
        rows.add(List.of("DEMO_CLIENT_AWARE_Q2_CTV_2025_05_ROKU_PREM_1001_NA",      "Premium CTV",          "CTV",     "1,840,221", "—",      "$45,000"));
        rows.add(List.of("DEMO_CLIENT_AWARE_Q2_DSP_2025_05_TTD_OPEN_1002_NA",       "Programmatic Display", "Display", "8,210,440", "32,114", "$30,000"));
        rows.add(List.of("DEMO_CLIENT_AWARE_Q2_VID_2025_05_YT_INST_1003_NA",        "Online Video",         "Video",   "3,109,002", "12,448", "$25,000"));
        rows.add(List.of("DEMO_CLIENT_AWARE_Q2_AUD_2025_05_SPOT_STR_1004_NA",       "Streaming Audio",      "Audio",   "2,400,118", "—",      "$20,000"));
        return rows;
    }

    private List<List<String>> audienceFixture() {
        return List.of(
            List.of("Segment", "Reach", "Frequency"),
            List.of("A18-34", "1,200,000", "3.2"),
            List.of("A35-54", "980,000",   "2.8")
        );
    }

    private List<List<String>> estimatesFixture() {
        return List.of(
            List.of("Line", "Planned Impr", "Planned Spend"),
            List.of("CTV",     "1,800,000", "$45,000"),
            List.of("Display", "8,000,000", "$30,000")
        );
    }

    private List<List<String>> geoFixture() {
        return List.of(
            List.of("DMA", "Share"),
            List.of("New York",    "32%"),
            List.of("Los Angeles", "21%"),
            List.of("Chicago",     "14%")
        );
    }
}
