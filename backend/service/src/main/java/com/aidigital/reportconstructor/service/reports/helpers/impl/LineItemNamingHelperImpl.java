package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import org.springframework.stereotype.Component;

/**
 * Spring bean implementation of {@link LineItemNamingHelper}.
 */
@Component
public class LineItemNamingHelperImpl implements LineItemNamingHelper {

	@Override
	public String extractLineItemId(String naming) {
		if (naming == null) {
			return null;
		}
		String[] parts = naming.split("_");
		if (parts.length < 9) {
			return null;
		}
		String id = parts[8].trim();
		if (id.isEmpty() || id.equals("-")) {
			return null;
		}
		return id.chars().allMatch(Character::isDigit) ? id : null;
	}

	@Override
	public String extractLineItemIdOrBlank(String naming) {
		String id = extractLineItemId(naming);
		return id == null ? "" : id;
	}
}
