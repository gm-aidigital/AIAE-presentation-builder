package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring bean implementation of {@link ReportNumberParser}.
 */
@Component
public class ReportNumberParserImpl implements ReportNumberParser {

    private static final Pattern LEADING_NUM = Pattern.compile("^[0-9]*\\.?[0-9]+");

    @Override
    public double parseReportNumber(String raw) {
        if (raw == null) {
            return 0.0;
        }
        String s = raw.replace(",", "").replaceAll("[^0-9.]", "");
        Matcher m = LEADING_NUM.matcher(s);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
