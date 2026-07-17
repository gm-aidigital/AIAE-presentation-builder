package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownSelection;
import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.dto.CreativeRow;
import com.aidigital.reportconstructor.service.reports.dto.CreativeTable;
import com.aidigital.reportconstructor.service.reports.helpers.BreakdownSelectionResolver;
import com.aidigital.reportconstructor.service.reports.helpers.ReportSheetHelper;
import com.aidigital.reportconstructor.service.reports.helpers.impl.CreativeBreakdownHelperImpl;
import com.aidigital.reportconstructor.service.reports.ports.ClaudeClient;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.Shape;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import com.google.api.services.slides.v1.model.TextRun;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RealSlidesProviderTest {

	private RealSlidesProvider newProvider(Map<Integer, String> tacticSlideObjectIds) {
		GoogleCredentialsFactory creds = Mockito.mock(GoogleCredentialsFactory.class);
		when(creds.transport()).thenReturn(new NetHttpTransport());
		when(creds.jsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
		when(creds.initializer()).thenReturn(request -> {
		});
		GoogleProperties props = Mockito.mock(GoogleProperties.class);
		when(props.getSlidesTemplateId()).thenReturn("template");
		when(props.getSlidesTargetFolderId()).thenReturn("");
		when(props.getTacticSlideObjectIds()).thenReturn(tacticSlideObjectIds);
		DriveSharer driveSharer = Mockito.mock(DriveSharer.class);
		GoogleRequestRetrier retrier = Mockito.mock(GoogleRequestRetrier.class);
		return new RealSlidesProvider(creds, props, driveSharer, retrier);
	}

	private Page slide(String objectId) {
		return new Page().setObjectId(objectId);
	}

	private Page shapeSlide(String objectId, List<List<String>> textRunsPerShape) {
		Page page = new Page().setObjectId(objectId);
		java.util.List<PageElement> elements = new java.util.ArrayList<>();
		for (List<String> runs : textRunsPerShape) {
			java.util.List<TextElement> textElements = new java.util.ArrayList<>();
			for (String run : runs) {
				textElements.add(new TextElement().setTextRun(new TextRun().setContent(run)));
			}
			elements.add(new PageElement().setShape(new Shape().setText(new TextContent().setTextElements(textElements))));
		}
		page.setPageElements(elements);
		return page;
	}

	@Test
	void renumber_replacesOnlyTheStandaloneNVariableTest() {
		// Given: a provider and tokens with n in various delimiter contexts
		RealSlidesProvider provider = newProvider(Map.of());

		// When-Then: only the standalone n is renumbered; an n inside a word stays untouched
		assertThat(provider.renumber("{{tactic n}}", 3)).isEqualTo("{{tactic 3}}");
		assertThat(provider.renumber("{{publisher_n.1}}", 3)).isEqualTo("{{publisher_3.1}}");
		assertThat(provider.renumber("{{cr_live_n}}", 12)).isEqualTo("{{cr_live_12}}");
		assertThat(provider.renumber("{{tactic n top creative name n.1}}", 3))
				.isEqualTo("{{tactic 3 top creative name 3.1}}");
		assertThat(provider.renumber("{{cr_takeaway_tactic n_1}}", 7)).isEqualTo("{{cr_takeaway_tactic 7_1}}");
		// The n in "name" / "imps" / "geo" must not change:
		assertThat(provider.renumber("{{tactic n.2 top creative imps}}", 5))
				.isEqualTo("{{tactic 5.2 top creative imps}}");
	}

	@Test
	void extractRenumberableTokens_reassemblesTokensSplitAcrossRunsTest() {
		// Given: a master slide whose token "{{publisher_n.1}}" is split across three text runs, plus a
		// table cell carrying another token
		RealSlidesProvider provider = newProvider(Map.of());
		Page master = shapeSlide("m_tp", List.of(List.of("{{publisher_", "n", ".1}}")));
		master.getPageElements().add(new PageElement().setTable(new Table().setTableRows(List.of(
				new TableRow().setTableCells(List.of(new TableCell().setText(new TextContent().setTextElements(
						List.of(new TextElement().setTextRun(new TextRun().setContent("{{pub_imp_n.1}}")))))))))));

		// When:
		Set<String> tokens = provider.extractRenumberableTokens(master);

		// Then: the split token is reassembled and the table-cell token is found
		assertThat(tokens).containsExactlyInAnyOrder("{{publisher_n.1}}", "{{pub_imp_n.1}}");
	}

	@Test
	void resolveBreakdownMasterIds_dropsUnknownCodesAndBlankIdsTest() {
		// Given: a config map with a valid code, a blank id, and an unknown code
		RealSlidesProvider provider = newProvider(Map.of());
		Map<String, String> configured = new LinkedHashMap<>();
		configured.put("tp", "m_tp");
		configured.put("ca", "  ");
		configured.put("bogus", "x");

		// When:
		Map<BreakdownType, String> resolved = provider.resolveBreakdownMasterIds(configured);

		// Then: only the valid, non-blank entry survives
		assertThat(resolved).containsExactly(Map.entry(BreakdownType.TOP_PUBLISHERS, "m_tp"));
	}

	@Test
	void buildBreakdownRequests_duplicatesRenumbersPositionsAndDeletesMastersTest() {
		// Given: a deck with tactic slides 1..3 followed by the master slides, tactic 1 enables Top
		// Publishers + Device and tactic 3 enables Top Publishers (tactic 2 enables nothing)
		Map<Integer, String> tacticSlideObjectIds = new LinkedHashMap<>();
		tacticSlideObjectIds.put(1, "tactic1");
		tacticSlideObjectIds.put(2, "tactic2");
		tacticSlideObjectIds.put(3, "tactic3");
		RealSlidesProvider provider = newProvider(tacticSlideObjectIds);

		List<Page> deck = List.of(
				slide("tactic1"), slide("tactic2"), slide("tactic3"),
				shapeSlide("m_tp", List.of(List.of("{{tactic n}}"), List.of("{{publisher_", "n", ".1}}"))),
				slide("m_ca"),
				shapeSlide("m_dev", List.of(List.of("{{top_dev_n}}"))));

		Map<BreakdownType, String> masterIds = new LinkedHashMap<>();
		masterIds.put(BreakdownType.TOP_PUBLISHERS, "m_tp");
		masterIds.put(BreakdownType.CREATIVE, "m_ca");
		masterIds.put(BreakdownType.GEO, "m_geo"); // configured but absent from the deck
		masterIds.put(BreakdownType.DEVICE, "m_dev");

		Map<Integer, Set<BreakdownType>> enabledByTactic = new LinkedHashMap<>();
		enabledByTactic.put(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS, BreakdownType.DEVICE));
		enabledByTactic.put(2, EnumSet.noneOf(BreakdownType.class));
		enabledByTactic.put(3, EnumSet.of(BreakdownType.TOP_PUBLISHERS));

		// When:
		List<Request> requests = provider.buildBreakdownRequests(deck, masterIds, enabledByTactic, Map.of());

		// Then: three duplicates — tp+dev for tactic 1 (enum order), tp for tactic 3
		List<Request> dups = requests.stream().filter(r -> r.getDuplicateObject() != null).toList();
		assertThat(dups).hasSize(3);
		assertThat(dups.get(0).getDuplicateObject().getObjectIds()).containsExactly(Map.entry("m_tp", "bd_tp_1"));
		assertThat(dups.get(1).getDuplicateObject().getObjectIds()).containsExactly(Map.entry("m_dev", "bd_dev_1"));
		assertThat(dups.get(2).getDuplicateObject().getObjectIds()).containsExactly(Map.entry("m_tp", "bd_tp_3"));

		// And: the publisher token is renumbered per copy, scoped to that copy only
		assertThat(requests).anyMatch(r -> r.getReplaceAllText() != null
				&& r.getReplaceAllText().getContainsText().getText().equals("{{publisher_n.1}}")
				&& r.getReplaceAllText().getReplaceText().equals("{{publisher_1.1}}")
				&& r.getReplaceAllText().getPageObjectIds().equals(List.of("bd_tp_1")));
		assertThat(requests).anyMatch(r -> r.getReplaceAllText() != null
				&& r.getReplaceAllText().getContainsText().getText().equals("{{publisher_n.1}}")
				&& r.getReplaceAllText().getReplaceText().equals("{{publisher_3.1}}")
				&& r.getReplaceAllText().getPageObjectIds().equals(List.of("bd_tp_3")));

		// And: each tactic's copies are placed after its main slide, in ascending main-slide order, with
		// the running count of already-inserted copies added to keep the index valid
		List<Request> positions = requests.stream().filter(r -> r.getUpdateSlidesPosition() != null).toList();
		assertThat(positions).hasSize(2);
		assertThat(positions.get(0).getUpdateSlidesPosition().getSlideObjectIds())
				.containsExactly("bd_tp_1", "bd_dev_1");
		assertThat(positions.get(0).getUpdateSlidesPosition().getInsertionIndex()).isEqualTo(1);
		assertThat(positions.get(1).getUpdateSlidesPosition().getSlideObjectIds()).containsExactly("bd_tp_3");
		assertThat(positions.get(1).getUpdateSlidesPosition().getInsertionIndex()).isEqualTo(5);

		// And: every configured master present in the deck is deleted (m_geo is absent, so skipped)
		List<String> deleted = requests.stream()
				.filter(r -> r.getDeleteObject() != null)
				.map(r -> r.getDeleteObject().getObjectId())
				.toList();
		assertThat(deleted).containsExactlyInAnyOrder("m_tp", "m_ca", "m_dev");

		// And: phase order holds — all duplicates precede all positions, which precede all deletes
		int lastDup = lastIndexOf(requests, r -> r.getDuplicateObject() != null);
		int firstPos = firstIndexOf(requests, r -> r.getUpdateSlidesPosition() != null);
		int lastPos = lastIndexOf(requests, r -> r.getUpdateSlidesPosition() != null);
		int firstDel = firstIndexOf(requests, r -> r.getDeleteObject() != null);
		assertThat(lastDup).isLessThan(firstPos);
		assertThat(lastPos).isLessThan(firstDel);
	}

	@Test
	void buildBreakdownRequests_writesTheValueWhenOneIsKnownForTheRenumberedTokenTest() {
		// Given: tactic 1 enables Top Publishers and its first publisher row's value is known. The deck's own
		// placeholder pass has already run by the time these copies exist, so a token left merely renumbered
		// here would ship raw.
		RealSlidesProvider provider = newProvider(Map.of(1, "tactic1"));
		List<Page> deck = List.of(
				slide("tactic1"),
				shapeSlide("m_tp", List.of(List.of("{{publisher_", "n", ".1}}"), List.of("{{pub_sov_n.1}}"))));
		Map<BreakdownType, String> masterIds = Map.of(BreakdownType.TOP_PUBLISHERS, "m_tp");
		Map<Integer, Set<BreakdownType>> enabledByTactic = Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		Map<String, String> values = Map.of("{{publisher_1.1}}", "YouTube");

		// When:
		List<Request> requests = provider.buildBreakdownRequests(deck, masterIds, enabledByTactic, values);

		// Then: the known token goes straight from its generic form to the final value, scoped to the copy
		assertThat(requests).anyMatch(r -> r.getReplaceAllText() != null
				&& r.getReplaceAllText().getContainsText().getText().equals("{{publisher_n.1}}")
				&& r.getReplaceAllText().getReplaceText().equals("YouTube")
				&& r.getReplaceAllText().getPageObjectIds().equals(List.of("bd_tp_1")));

		// Then: nothing leaves that token in its intermediate renumbered form
		assertThat(requests).noneMatch(r -> r.getReplaceAllText() != null
				&& r.getReplaceAllText().getReplaceText().equals("{{publisher_1.1}}"));

		// Then: a token with no known value still falls back to a plain renumber
		assertThat(requests).anyMatch(r -> r.getReplaceAllText() != null
				&& r.getReplaceAllText().getContainsText().getText().equals("{{pub_sov_n.1}}")
				&& r.getReplaceAllText().getReplaceText().equals("{{pub_sov_1.1}}"));
	}

	@Test
	void buildBreakdownRequests_blanksATokenWhoseKnownValueIsEmptyTest() {
		// Given: tactic 1's observation bullet resolved to blank (its publisher table was never filled in)
		RealSlidesProvider provider = newProvider(Map.of(1, "tactic1"));
		List<Page> deck = List.of(
				slide("tactic1"),
				shapeSlide("m_tp", List.of(List.of("{{publishers_observation_", "n", "_1}}"))));
		Map<BreakdownType, String> masterIds = Map.of(BreakdownType.TOP_PUBLISHERS, "m_tp");
		Map<Integer, Set<BreakdownType>> enabledByTactic = Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));
		Map<String, String> values = Map.of("{{publishers_observation_1_1}}", "");

		// When:
		List<Request> requests = provider.buildBreakdownRequests(deck, masterIds, enabledByTactic, values);

		// Then: the token is replaced with nothing rather than left on the slide
		assertThat(requests).anyMatch(r -> r.getReplaceAllText() != null
				&& r.getReplaceAllText().getContainsText().getText().equals("{{publishers_observation_n_1}}")
				&& r.getReplaceAllText().getReplaceText().isEmpty());
	}

	@Test
	void buildBreakdownRequests_isNoopWhenNoTacticSlideMatchesTest() {
		// Given: a tactic enables a breakdown, but its main slide is not present in the deck
		RealSlidesProvider provider = newProvider(Map.of(1, "tactic1"));
		List<Page> deck = List.of(shapeSlide("m_tp", List.of(List.of("{{tactic n}}"))));
		Map<BreakdownType, String> masterIds = Map.of(BreakdownType.TOP_PUBLISHERS, "m_tp");
		Map<Integer, Set<BreakdownType>> enabledByTactic =
				Map.of(1, EnumSet.of(BreakdownType.TOP_PUBLISHERS));

		// When:
		List<Request> requests = provider.buildBreakdownRequests(deck, masterIds, enabledByTactic, Map.of());

		// Then: nothing is emitted because tactic 1's main slide is absent
		assertThat(requests).isEmpty();
	}

	private int firstIndexOf(List<Request> requests, java.util.function.Predicate<Request> predicate) {
		for (int i = 0; i < requests.size(); i++) {
			if (predicate.test(requests.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private int lastIndexOf(List<Request> requests, java.util.function.Predicate<Request> predicate) {
		for (int i = requests.size() - 1; i >= 0; i--) {
			if (predicate.test(requests.get(i))) {
				return i;
			}
		}
		return -1;
	}

	@Test
	void renumber_agreesWithTheKeysCreativeBreakdownHelperBuildsTest() {
		// Given: the Creative analysis master's real tokens, and the token->value map the helper hands to
		// addBreakdownSlides for the same tactic. The two are written in different modules from the same
		// spec, so nothing but this test stops them drifting: a helper key that does not match the master's
		// renumbered token leaves the slide either blank or showing a raw token.
		RealSlidesProvider provider = newProvider(Map.of(1, "main-1"));
		ReportSheetHelper sheetHelper = Mockito.mock(ReportSheetHelper.class);
		BreakdownSelectionResolver resolver = Mockito.mock(BreakdownSelectionResolver.class);
		ClaudeClient claude = Mockito.mock(ClaudeClient.class);
		CreativeBreakdownHelperImpl helper = new CreativeBreakdownHelperImpl(sheetHelper, resolver, claude);

		List<BreakdownSelection> selections = List.of(new BreakdownSelection(1, List.of("ca")));
		when(resolver.resolve(selections)).thenReturn(Map.of(1, EnumSet.of(BreakdownType.CREATIVE)));
		when(sheetHelper.readCreativeTables("sheet", Set.of(1), "token")).thenReturn(Map.of(
				1, new CreativeTable("6", "11.04", "2.09", "CH-Ad-320-50-1B", List.of(
						new CreativeRow("CH-Ad-320-50-1B", "144,070", "1.77%", "", "$861.81")))));
		when(claude.batchCreativeTakeaways(Mockito.any(), Mockito.eq("brief")))
				.thenReturn(Map.of(1, List.of("t1", "t2", "t3", "t4")));
		Map<String, String> values = helper.buildCreativeValues(
				"sheet", selections, Map.of("{{tactic 1}}", "Display", "{{tactic 1 KPI type}}", "CTR"),
				"brief", "token").values();

		// When: every master token is renumbered exactly as buildBreakdownRequests renumbers it
		List<String> masterTokens = List.of(
				"{{tactic n}}", "{{tactic n KPI type}}",
				"{{cr_live_n}}", "{{cr_bKPI_n}}", "{{cr_aKPI_n}}",
				"{{tactic n top creative name}}",
				"{{tactic n top creative name n.1}}", "{{tactic n.1 top creative imps}}",
				"{{tactic n.1 top creative ctr}}", "{{tactic n.1 top creative vcr}}",
				"{{tactic n.1 top creative spend}}",
				"{{tactic n top creative name n.5}}", "{{tactic n.5 top creative spend}}",
				"{{cr_takeaway_tactic n_1}}", "{{cr_takeaway_tactic n_2}}",
				"{{cr_takeaway_tactic n_3}}", "{{cr_takeaway_tactic n_4}}");

		// Then: every one of them resolves to a key the helper actually produced, so none can fall through
		// to the renumber-only path and ship raw
		for (String token : masterTokens) {
			String concrete = provider.renumber(token, 1);
			assertThat(values)
					.as("master token %s renumbers to %s, which the helper must have a value for",
							token, concrete)
					.containsKey(concrete);
		}

		// Then: the takeaway tokens specifically carry Claude's copy rather than an empty string
		assertThat(values.get(provider.renumber("{{cr_takeaway_tactic n_1}}", 1))).isEqualTo("t1");
		assertThat(values.get(provider.renumber("{{cr_takeaway_tactic n_4}}", 1))).isEqualTo("t4");

		// Then: the stat tiles and the row carry the sheet's values
		assertThat(values.get(provider.renumber("{{cr_live_n}}", 1))).isEqualTo("6");
		assertThat(values.get(provider.renumber("{{tactic n.1 top creative imps}}", 1))).isEqualTo("144,070");
	}
}
