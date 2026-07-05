package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link ReportNumberParser}.
 */
@Component
public class ReportNumberParserImpl implements ReportNumberParser {

	private static final double THOUSAND = 1_000d;
	private static final double MILLION = 1_000_000d;
	private static final double BILLION = 1_000_000_000d;

	/**
	 * Matches the first number in a cell (with optional thousands commas and decimals) plus an
	 * optional magnitude suffix {@code k}/{@code m}/{@code b}. Currency symbols, percent signs and
	 * surrounding text are ignored because {@link Matcher#find()} scans to the first numeric run.
	 */
	private static final Pattern NUM_WITH_SUFFIX =
			Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbB])?");

	@Override
	public double parseReportNumber(String raw) {
		if (raw == null) {
			return 0.0;
		}
		Matcher m = NUM_WITH_SUFFIX.matcher(raw);
		if (m.find()) {
			try {
				double base = Double.parseDouble(m.group(1).replace(",", ""));
				return base * magnitude(m.group(2));
			} catch (NumberFormatException ignored) {
				return 0.0;
			}
		}
		return 0.0;
	}

	/**
	 * Maps a compact-number magnitude suffix to its multiplier so values the generated sheet stores
	 * compactly (e.g. {@code "74k"}, {@code "1.2M"}) parse back to their true magnitude instead of
	 * losing the suffix.
	 *
	 * @param suffix the captured magnitude letter ({@code k}/{@code m}/{@code b}), or {@code null}/empty
	 *               when the number carried no suffix
	 * @return the multiplier to apply to the parsed base number ({@code 1} when no suffix)
	 */
	double magnitude(String suffix) {
		if (suffix == null || suffix.isEmpty()) {
			return 1d;
		}
		return switch (suffix.toLowerCase(Locale.ROOT)) {
			case "k" -> THOUSAND;
			case "m" -> MILLION;
			case "b" -> BILLION;
			default -> 1d;
		};
	}
}
