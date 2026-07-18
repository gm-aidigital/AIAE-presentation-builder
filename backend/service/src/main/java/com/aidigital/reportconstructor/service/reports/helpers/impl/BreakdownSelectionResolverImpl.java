package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring bean implementation of {@link BreakdownSelectionResolver}.
 */
@Component
public class BreakdownSelectionResolverImpl implements BreakdownSelectionResolver {

	@Override
	public Map<Integer, Set<BreakdownType>> resolve(List<BreakdownSelection> selections) {
		Map<Integer, Set<BreakdownType>> enabledByTactic = new LinkedHashMap<>();
		if (selections == null) {
			return enabledByTactic;
		}
		for (BreakdownSelection selection : selections) {
			if (selection == null || selection.tacticNum() == null) {
				continue;
			}
			Set<BreakdownType> enabled = EnumSet.noneOf(BreakdownType.class);
			if (selection.breakdowns() != null) {
				for (String code : selection.breakdowns()) {
					if (code == null || code.isBlank()) {
						continue;
					}
					BreakdownType type = BreakdownType.BY_CODE.get(code.trim().toLowerCase());
					if (type != null) {
						enabled.add(type);
					}
				}
			}
			enabledByTactic.put(selection.tacticNum(), enabled);
		}
		return enabledByTactic;
	}
}
