package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.services.LabelChip;
import com.aidigital.reportconstructor.service.reports.services.Labels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderLabelCollectorImplTest {

	private final PlaceholderLabelCollectorImpl collector = new PlaceholderLabelCollectorImpl();

	@Test
	void shouldCollectLabelChipsFromSheetAndAdjustmentsTest() {
		GeneratePayload payload = new GeneratePayload(
				"brief",
				"standard",
				List.of(
						List.of("Client name:", "Acme"),
						List.of("  ", "ignored"),
						List.of("Campaign:", "")
				),
				List.of(List.of("Audience age:", "25-34")),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				""
		);

		Labels labels = collector.collectAllLabels(payload);

		assertThat(labels.sheet()).containsExactly(new LabelChip("Client name:", "Acme"));
		assertThat(labels.adj()).containsExactly(new LabelChip("Audience age:", "25-34"));
	}
}
