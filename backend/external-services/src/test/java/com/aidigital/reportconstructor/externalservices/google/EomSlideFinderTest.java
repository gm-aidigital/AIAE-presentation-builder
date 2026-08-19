package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EomSlideFinderTest {

	private final EomSlideFinder finder = new EomSlideFinder();

	private TextContent text(String content) {
		return new TextContent().setTextElements(List.of(
				new TextElement().setTextRun(new TextRun().setContent(content))));
	}

	private Page shapeSlide(String objectId, String... texts) {
		List<PageElement> elements = new java.util.ArrayList<>();
		for (String content : texts) {
			elements.add(new PageElement().setShape(new Shape().setText(text(content))));
		}
		return new Page().setObjectId(objectId).setPageElements(elements);
	}

	private Page tableSlide(String objectId, String... cellTexts) {
		List<TableCell> cells = new java.util.ArrayList<>();
		for (String content : cellTexts) {
			cells.add(new TableCell().setText(text(content)));
		}
		return new Page().setObjectId(objectId).setPageElements(List.of(new PageElement()
				.setTable(new Table().setTableRows(List.of(new TableRow().setTableCells(cells))))));
	}

	@Test
	void findsBothTacticMastersAndKeepsTemplateOrder() {
		List<Page> deck = List.of(
				shapeSlide("cover", "EOM REPORT - MONTH {{mon no}} OF {{total mon no}}"),
				shapeSlide("eoc-style-master", "{{tactic n}}", "{{tactic n overview}}"),
				shapeSlide("eom-channel-master", "{{tactic n planned imps}}", "{{pacing n next month}}"));

		assertThat(finder.tacticMasterSlideIds(deck))
				.containsExactly("eoc-style-master", "eom-channel-master");
	}

	@Test
	void breakdownMastersAreNotMistakenForTacticMasters() {
		List<Page> deck = List.of(
				shapeSlide("tactic-master", "{{tactic n}}", "{{tactic n imps}}"),
				tableSlide("publishers", "Delivery Breakdown – {{tactic n}}", "{{publisher_n.1}}"),
				shapeSlide("creative", "{{cr_live_n}}", "{{tactic n top creative name}}"),
				tableSlide("geo", "{{geo_n.1}}", "{{geo_imp_n.1}}"),
				shapeSlide("audience", "{{age_n_gr}}", "{{tactic n male}}"),
				tableSlide("device", "{{dev_n_ctr}}", "{{mobile_imps_n}}"));

		assertThat(finder.tacticMasterSlideIds(deck)).containsExactly("tactic-master");
		assertThat(finder.breakdownMasterSlideIds(deck)).containsExactlyInAnyOrderEntriesOf(Map.of(
				BreakdownType.TOP_PUBLISHERS, "publishers",
				BreakdownType.CREATIVE, "creative",
				BreakdownType.GEO, "geo",
				BreakdownType.AUDIENCE, "audience",
				BreakdownType.DEVICE, "device"));
	}

	@Test
	void dropsOnlyDashboardSlidesWhoseTacticSlotsAreAllUnused() {
		List<Page> deck = List.of(
				tableSlide("pacing-1-7", "{{tactic 1 planned imps}}", "{{tactic 7 planned imps}}"),
				tableSlide("pacing-8-14", "{{tactic 8 planned imps}}", "{{tactic 14 planned imps}}"),
				tableSlide("perf-1-7", "{{tactic 1 KPI}}", "{{tactic 7 KPI}}"),
				tableSlide("perf-15-21", "{{tactic 15 KPI}}", "{{tactic 21 KPI}}"));

		assertThat(finder.surplusTacticSlideIds(deck, 3)).containsExactly("pacing-8-14", "perf-15-21");
		assertThat(finder.surplusTacticSlideIds(deck, 28)).isEmpty();
		assertThat(finder.surplusTacticSlideIds(deck, 8)).containsExactly("perf-15-21");
	}

	@Test
	void channelDividerAndMastersSurviveTheSurplusPass() {
		List<Page> deck = List.of(
				shapeSlide("divider", "{{tactic 1}}", "{{tactic 2}}", "{{tactic 3}}"),
				shapeSlide("master", "{{tactic n}}", "{{tactic n imps}}"),
				shapeSlide("static", "CHANNEL-BY-CHANNEL BREAKDOWN"));

		assertThat(finder.surplusTacticSlideIds(deck, 1)).isEmpty();
	}

	@Test
	void wordsContainingNAreNotMistakenForTheTacticVariable() {
		List<Page> deck = List.of(
				shapeSlide("static", "{{client_name}}", "{{flight_dates}}", "{{total_investment}}"));

		assertThat(finder.tacticMasterSlideIds(deck)).isEmpty();
	}

	@Test
	void emptyOrNullDeckIsHandled() {
		assertThat(finder.tacticMasterSlideIds(null)).isEmpty();
		assertThat(finder.breakdownMasterSlideIds(List.of())).isEmpty();
		assertThat(finder.surplusTacticSlideIds(null, 3)).isEmpty();
	}
}
