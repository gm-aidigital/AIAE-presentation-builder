package com.aidigital.reportconstructor.service.reports.engine;

import com.aidigital.reportconstructor.service.reports.helpers.LineItemNamingHelper;
import com.aidigital.reportconstructor.service.reports.helpers.ReportNumberParser;
import com.aidigital.reportconstructor.service.reports.helpers.SheetRowHelper;
import com.aidigital.reportconstructor.service.reports.helpers.TacticExtractionHelper;
import com.aidigital.reportconstructor.service.reports.helpers.impl.LineItemNamingHelperImpl;
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
		return new TacticExtractionHelperImpl(tacticCatalog(), sheetRowHelper());
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

	static CampaignResolvers campaignResolvers() {
		return new CampaignResolvers(sheetRowHelper(), fmt(), tacticExtractionHelper());
	}

	static TacticResolvers tacticResolvers() {
		return new TacticResolvers(sheetRowHelper(), fmt(), tacticExtractionHelper(), campaignResolvers());
	}

	public static PlaceholderSectionBuilderImpl placeholderSectionBuilder() {
		return new PlaceholderSectionBuilderImpl(campaignResolvers(), tacticResolvers(), tacticExtractionHelper());
	}

	public static PlaceholderClaudeGateImpl placeholderClaudeGate() {
		return new PlaceholderClaudeGateImpl(sheetRowHelper());
	}

	public static CampaignDataCollector campaignDataCollector() {
		return new CampaignDataCollector(sheetRowHelper(), tacticExtractionHelper(), campaignResolvers());
	}

	public static ChartPivot chartPivot() {
		return new ChartPivot(sheetRowHelper(), lineItemNamingHelper(), reportNumberParser());
	}
}
