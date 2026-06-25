package com.aidigital.reportconstructor.service.reports.services.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.CampaignFrequencies;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.Placeholder;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;
import com.aidigital.reportconstructor.service.reports.engine.CampaignDataCollector;
import com.aidigital.reportconstructor.service.reports.engine.CampaignResolvers;
import com.aidigital.reportconstructor.service.reports.engine.ReportClaudeDefaults;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderClaudeGate;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderLabelCollector;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderSectionBuilder;
import com.aidigital.reportconstructor.service.reports.helpers.PlaceholderValueFlattener;
import com.aidigital.reportconstructor.service.reports.services.PlaceholderResolverService;
import com.aidigital.reportconstructor.service.reports.services.PreviewResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Facade composing placeholder helpers for preview and Slides replacement generation.
 */
@Service
@RequiredArgsConstructor
public class PlaceholderResolverServiceImpl implements PlaceholderResolverService {

	private final CampaignDataCollector campaignDataCollector;
	private final CampaignResolvers campaignResolvers;
	private final PlaceholderSectionBuilder sectionBuilder;
	private final PlaceholderClaudeGate claudeGate;
	private final PlaceholderLabelCollector labelCollector;
	private final PlaceholderValueFlattener valueFlattener;
	private final ReportClaudeDefaults claudeDefaults;

	@Override
	public PreviewResult resolve(GeneratePayload payload) {
		CampaignData data = collectData(payload);
		List<PreviewSection> sections = sectionBuilder.buildSections(
				payload, data,
				claudeDefaults.emptyStrategic(), claudeDefaults.emptyTactical(), claudeDefaults.emptyResults(), null
		);

		int total = 0;
		int found = 0;
		for (PreviewSection sec : sections) {
			for (Placeholder ph : sec.placeholders()) {
				total++;
				if (!"not_found".equals(ph.source())) {
					found++;
				}
			}
		}
		int sheetCount = payload.sheetRows() == null ? 0 : payload.sheetRows().size();
		int adjCount = payload.adjRows() == null ? 0 : payload.adjRows().size();
		return new PreviewResult(sections, labelCollector.collectAllLabels(payload), found, total, sheetCount,
				adjCount);
	}

	@Override
	public Map<String, String> buildFlatReplacements(
			GeneratePayload payload,
			CampaignData data,
			ClaudeStrategic ccA,
			ClaudeTactical ccB,
			ClaudeResults ccC,
			String geoSummary
	) {
		List<PreviewSection> sections = sectionBuilder.buildSections(payload, data, ccA, ccB, ccC, geoSummary);
		return valueFlattener.buildFlatReplacements(sections);
	}

	@Override
	public CampaignData collectData(GeneratePayload payload) {
		return campaignDataCollector.collect(
				payload.sheetRows(), payload.adjRows(), payload.audienceRows(),
				payload.estimatesRows(), payload.lineItemMapping()
		);
	}

	@Override
	public CampaignFrequencies computeFrequencies(GeneratePayload payload, CampaignData data) {
		return campaignResolvers.computeFrequencies(
				payload.estimatesRows(), payload.sheetRows(), payload.adjRows(), data);
	}

	@Override
	public boolean needStrategic(GeneratePayload payload) {
		return claudeGate.needStrategic(payload);
	}

	@Override
	public boolean needTactical(GeneratePayload payload, CampaignData data) {
		return claudeGate.needTactical(payload, data);
	}

	@Override
	public boolean needResults(GeneratePayload payload, CampaignData data) {
		return claudeGate.needResults(payload, data);
	}

	@Override
	public boolean needGeoSummary(GeneratePayload payload) {
		return claudeGate.needGeoSummary(payload);
	}
}
