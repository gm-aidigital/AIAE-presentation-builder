package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.dto.SheetSummaryRow;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.SheetPlaceholderReader;
import com.aidigital.reportconstructor.service.reports.ports.UserGoogleTokenProvider;
import com.aidigital.reportconstructor.service.reports.services.SheetSummaryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default {@link SheetSummaryQueryService}: reads the generated workbook's first tab back into a
 * placeholder map (the same read/parse the slides-from-sheet pipeline uses) and projects each
 * tactic's plan/fact figures into a {@link SheetSummaryRow}.
 */
@Service
@RequiredArgsConstructor
public class SheetSummaryQueryServiceImpl implements SheetSummaryQueryService {

	/** Max tactics the report template carries (summary rows and "Main slide" blocks). */
	private static final int MAX_TACTICS = 7;

	/** Per-tactic placeholder tokens, assembled as {@code "{{tactic " + n + suffix + "}}"}. */
	private static final String TOKEN_PREFIX = "{{tactic ";
	private static final String TOKEN_SUFFIX = "}}";
	private static final String SUFFIX_NAME = "";
	private static final String SUFFIX_IMPS_PLAN = " imps plan";
	private static final String SUFFIX_IMPS_FACT = " imps";
	private static final String SUFFIX_SPEND_PLAN = " spend plan";
	private static final String SUFFIX_SPEND_FACT = " spend";

	private final ReportSheetHelper sheetHelper;
	private final SheetPlaceholderReader placeholderReader;
	private final ObjectProvider<UserGoogleTokenProvider> userGoogleTokens;

	@Override
	public List<SheetSummaryRow> readSummary(String sheetUrl, String callerUserId) {
		UserGoogleTokenProvider tokens = userGoogleTokens.getIfAvailable();
		String userGoogleToken = tokens == null ? null : tokens.googleAccessToken(callerUserId);

		List<List<String>> grid = sheetHelper.readSheetGrid(sheetUrl, userGoogleToken);
		Map<String, String> values = placeholderReader.readPlaceholders(grid);

		int count = tacticCount(values);
		List<SheetSummaryRow> rows = new ArrayList<>(count);
		for (int n = 1; n <= count; n++) {
			rows.add(new SheetSummaryRow(
					values.get(token(n, SUFFIX_NAME)),
					values.get(token(n, SUFFIX_IMPS_PLAN)),
					values.get(token(n, SUFFIX_IMPS_FACT)),
					values.get(token(n, SUFFIX_SPEND_PLAN)),
					values.get(token(n, SUFFIX_SPEND_FACT))));
		}
		return rows;
	}

	/**
	 * Builds a per-tactic placeholder token for the given tactic number and column suffix.
	 *
	 * @param n      1-based tactic number
	 * @param suffix the column suffix (e.g. {@code " spend plan"}; empty for the tactic name)
	 * @return the full token, e.g. {@code "{{tactic 2 spend plan}}"}
	 */
	String token(int n, String suffix) {
		return TOKEN_PREFIX + n + suffix + TOKEN_SUFFIX;
	}

	/**
	 * Counts the tactics present in the summary map: the number of contiguous {@code {{tactic n}}}
	 * name tokens starting at 1, clamped to {@link #MAX_TACTICS}.
	 *
	 * @param values the placeholder map read from the workbook
	 * @return the tactic count in {@code [0, MAX_TACTICS]}
	 */
	int tacticCount(Map<String, String> values) {
		int count = 0;
		for (int n = 1; n <= MAX_TACTICS; n++) {
			String name = values.get(token(n, SUFFIX_NAME));
			if (name == null || name.isBlank()) {
				break;
			}
			count = n;
		}
		return count;
	}
}
