package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;

import java.util.List;

/**
 * Assembles preview sections and per-tactic placeholder groups for the Slides deck.
 */
public interface PlaceholderSectionBuilder {

    /**
     * Builds every preview section, merging resolver output with Claude batch results.
     *
     * @param payload    constructor request supplying sheet/adjustments rows and report type
     * @param data         aggregated campaign/tactic metrics snapshot
     * @param ccA          Claude Batch A strategic copy
     * @param ccB          Claude Batch B tactical copy
     * @param ccC          Claude Batch C results copy
     * @param geoSummary   AI geo summary, or {@code null} when not used
     * @return ordered preview sections with their Russian UI titles
     */
    List<PreviewSection> buildSections(
        GeneratePayload payload,
        CampaignData data,
        ClaudeStrategic ccA,
        ClaudeTactical ccB,
        ClaudeResults ccC,
        String geoSummary
    );
}
