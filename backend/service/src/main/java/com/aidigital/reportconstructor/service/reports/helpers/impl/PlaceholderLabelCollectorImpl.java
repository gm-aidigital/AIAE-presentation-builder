package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderLabelCollector;
import com.aidigital.reportconstructor.service.reports.services.LabelChip;
import com.aidigital.reportconstructor.service.reports.services.Labels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring bean implementation of {@link PlaceholderLabelCollector}.
 */
@Component
@RequiredArgsConstructor
public class PlaceholderLabelCollectorImpl implements PlaceholderLabelCollector {

	@Override
	public Labels collectAllLabels(GeneratePayload payload) {
		return new Labels(
				collectLabelChips(payload.sheetRows()),
				collectLabelChips(payload.adjRows())
		);
	}

	List<LabelChip> collectLabelChips(List<List<String>> rows) {
		List<LabelChip> out = new ArrayList<>();
		if (rows == null) {
			return out;
		}
		for (List<String> row : rows) {
			if (row == null || row.isEmpty()) {
				continue;
			}
			String label = row.get(0) == null ? "" : row.get(0).trim();
			if (label.isEmpty()) {
				continue;
			}
			if (row.size() < 2 || row.get(1) == null) {
				continue;
			}
			String value = row.get(1).trim();
			if (value.isEmpty()) {
				continue;
			}
			out.add(new LabelChip(label, value));
		}
		return out;
	}
}
