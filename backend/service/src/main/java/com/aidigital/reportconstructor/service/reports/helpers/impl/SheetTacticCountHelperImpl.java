package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.SheetTacticCountHelper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring bean implementation of {@link SheetTacticCountHelper}.
 */
@Component
public class SheetTacticCountHelperImpl implements SheetTacticCountHelper {

	@Override
	public int countFromPlaceholders(Map<String, String> flatReplacements) {
		if (flatReplacements == null) {
			return 0;
		}
		int count = 0;
		for (int n = 1; n <= MAX_TACTICS; n++) {
			String name = flatReplacements.get("{{tactic " + n + "}}");
			if (name == null || name.isBlank()) {
				break;
			}
			count = n;
		}
		return count;
	}
}
