package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.aidigital.reportconstructor.service.reports.dto.SheetData;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
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

	private final GoogleCredentialsFactory creds;
	private final Sheets client;

	public RealGoogleSheetsProvider(GoogleCredentialsFactory creds) {
		this.creds = creds;
		this.client = new Sheets.Builder(creds.transport(), creds.jsonFactory(), creds.initializer())
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	@Override
	public boolean isLive() {
		return true;
	}

	@Override
	public SheetData fetchTab(String spreadsheetUrl, String tab, String userGoogleAccessToken) {
		boolean asUser = userGoogleAccessToken != null && !userGoogleAccessToken.isBlank();
		Sheets sheetsClient = asUser ? buildSheets(userGoogleAccessToken) : client;
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
			Spreadsheet meta = sheetsClient.spreadsheets().get(sheetId)
					.setFields("properties.title,sheets.properties.title,sheets.properties.hidden")
					.execute();
			String title = meta.getProperties() == null ? "Untitled" : meta.getProperties().getTitle();
			// Expose visible tabs only. Media plans routinely keep stale copies of
			// "Proposal"/"Estimates" as hidden tabs (often duplicating the visible
			// tab's name modulo a trailing space); including them lets the tool read
			// an outdated proposal. The latest data always lives on a visible tab.
			List<String> tabs = new ArrayList<>();
			for (Sheet s : meta.getSheets()) {
				SheetProperties props = s.getProperties();
				if (props != null && props.getTitle() != null && !Boolean.TRUE.equals(props.getHidden())) {
					tabs.add(props.getTitle());
				}
			}

			// Mirror PHP fetch_sheet.php: verify the tab exists before reading its
			// range. Optional tabs (e.g. "Geo") are often absent, and Google
			// reports a missing sheet as a confusing "Unable to parse range" 400.
			// Surface a clean 404 instead — the frontend already treats these
			// optional fetches as best-effort (.catch(() => null)). Resolve against
			// the visible tabs, tolerating trailing whitespace/case, so a request
			// for "Proposal" lands on a visible "Proposal " rather than a hidden
			// exact-name duplicate that no longer exists in the list.
			String resolvedTab = resolveVisibleTab(tab, tabs);
			if (resolvedTab == null) {
				throw new AppException(ErrorReason.C001,
						"Tab \"" + tab + "\" not found among visible tabs: " + String.join(", ", tabs));
			}

			String range = "'" + resolvedTab.replace("'", "''") + "'!A1:ZZ";
			ValueRange vr = sheetsClient.spreadsheets().values().get(sheetId, range).execute();
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
					sheetId, title, resolvedTab, tabs,
					rows.size(), headers.size(), headers,
					rows.stream().limit(5).toList(),
					rows
			);
		} catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
			int code = ex.getStatusCode();
			String googleMsg = ex.getDetails() != null && ex.getDetails().getMessage() != null
					? ex.getDetails().getMessage()
					: ex.getStatusMessage();
			log.error("[sheets] fetch failed for {} tab={} asUser={} (HTTP {})", sheetId, tab, asUser, code, ex);
			String hint;
			if (code == 403 || code == 404) {
				hint = asUser
						? "Your Google account can't open this spreadsheet — make sure you're signed in with an " +
                          "account that has at least Viewer access, or check the link"
						: "The report's Google service account can't open this spreadsheet — share it with the service" +
                          " account (Viewer access), or check the link";
			} else {
				hint = "Google Sheets request failed";
			}
			throw new AppException(ErrorReason.C000,
					hint + (googleMsg != null && !googleMsg.isBlank() ? " (" + googleMsg + ")" : "") + " [HTTP " + code + "]");
		} catch (IOException ex) {
			log.error("[sheets] fetch failed for {} tab={}", sheetId, tab, ex);
			throw new AppException(ErrorReason.C000, "Google Sheets fetch failed: " + ex.getMessage());
		}
	}

	/**
	 * Resolves a requested tab name against the visible tabs of the spreadsheet.
	 * Prefers an exact match; falls back to a trimmed, case-insensitive match so a
	 * request for {@code "Proposal"} still resolves when the current tab is named
	 * {@code "Proposal "} (a common pattern where an old exact-named tab was
	 * hidden and a fresh copy with a trailing space kept visible).
	 *
	 * @param requested   the tab name the caller asked for
	 * @param visibleTabs the titles of the spreadsheet's visible tabs
	 * @return the actual visible tab title to read, or {@code null} when none match
	 */
	String resolveVisibleTab(String requested, List<String> visibleTabs) {
		if (requested == null) {
			return null;
		}
		if (visibleTabs.contains(requested)) {
			return requested;
		}
		String normalized = requested.trim().toLowerCase();
		for (String candidate : visibleTabs) {
			if (candidate.trim().toLowerCase().equals(normalized)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Builds a Sheets client authenticated as the signed-in user via their
	 * short-lived Google OAuth access token (Clerk-brokered), so the spreadsheet
	 * only needs to be viewable by that user rather than shared with the service
	 * account. Mirrors {@code RealSlidesProvider#buildSlides}.
	 *
	 * @param accessToken the user's Google OAuth access token
	 * @return a Sheets client bound to the user's credentials
	 */
	Sheets buildSheets(String accessToken) {

		return new Sheets.Builder(creds.transport(), creds.jsonFactory(), userInitializer(accessToken))
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	/**
	 * Wraps a raw Google OAuth access token as an HTTP request initializer that
	 * authenticates each request as the token's owner.
	 *
	 * @param accessToken the user's Google OAuth access token
	 * @return an initializer bound to that user's credentials
	 */
	HttpRequestInitializer userInitializer(String accessToken) {

		return creds.withTimeout(new HttpCredentialsAdapter(
				GoogleCredentials.create(new AccessToken(accessToken, null))));
	}
}
