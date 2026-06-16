package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.helpers.ReportGenerationWarningsHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring bean implementation of {@link ReportGenerationWarningsHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationWarningsHelperImpl implements ReportGenerationWarningsHelper {

	private final ObjectMapper objectMapper;

	@Override
	public List<String> parseWarnings(String warningsJson) {
		if (warningsJson == null || warningsJson.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(warningsJson, new TypeReference<List<String>>() {
			});
		} catch (Exception ex) {
			log.warn("[report] could not parse warnings json: {}", ex.getMessage());
			return List.of();
		}
	}

	@Override
	public String serializeWarnings(List<String> warnings) {
		if (warnings == null || warnings.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(warnings);
		} catch (JsonProcessingException ex) {
			log.warn("[report] could not serialise warnings: {}", ex.getMessage());
			return null;
		}
	}
}
