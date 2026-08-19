package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.helpers.EffectiveTacticsHelper;
import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import com.aidigital.reportconstructor.service.reports.helpers.MediaPlanTacticExtractor;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.helpers.impl.EffectiveTacticsHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.LineItemNamingHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.MediaPlanTacticExtractorImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.PlaceholderClaudeGateImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.PlaceholderSectionBuilderImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.ReportNumberParserImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.SheetRowHelperImpl;
import com.aidigital.reportconstructor.service.reports.helpers.impl.TacticExtractionHelperImpl;

/**
 * Manual wiring for engine unit tests (no Spring context).
 */
public final class ReportsEngineTestSupport {

	private ReportsEngineTestSupport() {
	}

	static TacticCatalog tacticCatalog() {
		return new TacticCatalog();
	}

	static TacticExtractionHelper tacticExtractionHelper() {
		return new TacticExtractionHelperImpl(tacticCatalog(), mediaPlanTacticExtractor());
	}

	static MediaPlanTacticExtractor mediaPlanTacticExtractor() {
		return new MediaPlanTacticExtractorImpl(tacticCatalog(), sheetRowHelper());
	}

	static EffectiveTacticsHelper effectiveTacticsHelper() {
		return new EffectiveTacticsHelperImpl(mediaPlanTacticExtractor());
	}

	static SheetRowHelper sheetRowHelper() {
		return new SheetRowHelperImpl();
	}

	static LineItemNamingHelper lineItemNamingHelper() {
		return new LineItemNamingHelperImpl();
	}

	static ReportNumberParser reportNumberParser() {
		return new ReportNumberParserImpl();
	}

	static Fmt fmt() {
		return new Fmt();
	}

	static RatePlanCalculator ratePlanCalculator() {
		return new RatePlanCalculator();
	}

	static CampaignFlightResolver campaignFlightResolver() {
		return new CampaignFlightResolver(sheetRowHelper(), ratePlanCalculator());
	}

	static CampaignResolvers campaignResolvers() {
		return new CampaignResolvers(sheetRowHelper(), fmt(), tacticExtractionHelper(), ratePlanCalculator());
	}

	static TacticResolvers tacticResolvers() {
		return new TacticResolvers(sheetRowHelper(), fmt(), tacticExtractionHelper(), campaignResolvers(),
				ratePlanCalculator());
	}

	static SoWhatResolver soWhatResolver() {
		return new SoWhatResolver(campaignResolvers(), tacticExtractionHelper());
	}

	static FunnelChannelResolver funnelChannelResolver() {
		return new FunnelChannelResolver(campaignResolvers());
	}

	public static PlaceholderSectionBuilderImpl placeholderSectionBuilder() {
		return new PlaceholderSectionBuilderImpl(campaignResolvers(), tacticResolvers(), soWhatResolver(),
				funnelChannelResolver(), tacticExtractionHelper(), effectiveTacticsHelper());
	}

	public static PlaceholderClaudeGateImpl placeholderClaudeGate() {
		return new PlaceholderClaudeGateImpl(sheetRowHelper());
	}

	public static CampaignDataCollector campaignDataCollector() {
		return new CampaignDataCollector(sheetRowHelper(), tacticExtractionHelper(), campaignResolvers(),
				ratePlanCalculator(), effectiveTacticsHelper(), campaignFlightResolver());
	}

	public static ChartPivot chartPivot() {
		return new ChartPivot(sheetRowHelper(), lineItemNamingHelper(), reportNumberParser());
	}
}
