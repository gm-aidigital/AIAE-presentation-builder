package com.aidigital.reportconstructor.externalservices.anthropic;

import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchOption;
import com.aidigital.reportconstructor.service.reports.dto.LineItemMatchTactic;
import com.aidigital.reportconstructor.service.reports.ports.LineItemMatchAssistant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * No-op AI matcher — the only candidate when {@code ANTHROPIC_API_KEY} is unset and
 * {@link RealLineItemMatchAssistant} stays conditional-excluded. Returns no assignments, so
 * ambiguous channels fall back to manual drag-and-drop in the UI.
 */
@Component
public class StubLineItemMatchAssistant implements LineItemMatchAssistant {

	@Override
	public Map<Integer, String> match(List<LineItemMatchTactic> tactics, List<LineItemMatchOption> options) {
		return Map.of();
	}
}
