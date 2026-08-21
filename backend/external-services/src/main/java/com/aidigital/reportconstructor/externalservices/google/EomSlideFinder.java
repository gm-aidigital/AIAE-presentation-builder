package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.google.api.services.slides.v1.model.Page;
import com.google.api.services.slides.v1.model.PageElement;
import com.google.api.services.slides.v1.model.Table;
import com.google.api.services.slides.v1.model.TableCell;
import com.google.api.services.slides.v1.model.TableRow;
import com.google.api.services.slides.v1.model.TextContent;
import com.google.api.services.slides.v1.model.TextElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the slides an EOM deck build has to act on by reading the tokens already printed on them, instead
 * of by object id from configuration.
 *
 * <p>Ids would have to be collected by hand from the live template and re-collected after every redraw,
 * and a stale id fails silently — the slide is simply skipped. The tokens, by contrast, are the very thing
 * the slide exists to carry: a master tactic slide is the slide whose tokens spell the tactic variable
 * {@code n}, and a dashboard group slide is the slide whose tokens are numbered. That makes discovery
 * survive the template being rearranged, which is exactly what the EOM template is doing right now.
 *
 * <p>Deliberately EOM-only and self-contained (it walks page text itself rather than borrowing the EOC
 * provider's helpers) so that nothing here can change how an EOC deck is built.
 */
@Component
public class EomSlideFinder {

	/** Matches a whole {@code {{…}}} placeholder token (no nested braces). */
	private static final Pattern TOKEN = Pattern.compile("\\{\\{[^{}]*\\}\\}");

	/**
	 * Matches the standalone tactic variable {@code n} inside a token — a single {@code n}/{@code N}
	 * bounded by a token delimiter — so the {@code n} in words like {@code name} is never mistaken for it.
	 * A token that matches is a master token: it is written once in the template and renumbered per tactic.
	 */
	private static final Pattern N_VARIABLE = Pattern.compile("(?<=[_.\\s{])[nN](?=[_.\\s}])");

	/** Matches a tactic-numbered token such as {@code {{tactic 8 planned imps}}}, capturing the number. */
	private static final Pattern NUMBERED_TACTIC = Pattern.compile("\\{\\{tactic\\s+(\\d{1,2})[\\s}]");

	/**
	 * Token that identifies each breakdown master slide. Chosen to be unique to that slide in the EOM
	 * template and to be a master token (it carries the {@code n} variable), so a breakdown master can
	 * never be mistaken for a tactic master or the other way round.
	 */
	private static final Map<BreakdownType, String> BREAKDOWN_MARKERS = Map.of(
			BreakdownType.TOP_PUBLISHERS, "{{publisher_n.1}}",
			BreakdownType.CREATIVE, "{{cr_live_n}}",
			BreakdownType.GEO, "{{geo_n.1}}",
			BreakdownType.AUDIENCE, "{{age_n_gr}}",
			BreakdownType.DEVICE, "{{dev_n_ctr}}");

	/**
	 * Token that identifies the "Thoughts on tactic performance" master slide. Like the breakdown markers
	 * it is unique to that slide and carries the {@code n} variable, so the slide is recognised for what
	 * it is instead of being mistaken for a tactic master. That distinction matters: a tactic master is
	 * duplicated for every tactic, while this slide is duplicated only for the tactics that pass the
	 * ">2 breakdowns" gate — its text is written by the Step-3 Claude call, which runs for those tactics
	 * only, so a copy made for any other tactic would ship five raw tokens.
	 */
	static final String THOUGHTS_MARKER = "{{thoughts on tactic n performance 1}}";

	/**
	 * Finds the deck's master tactic slides: the slides carrying {@code n}-variable tokens that are
	 * neither breakdown masters nor the thoughts master. The EOM template has two of them (the channel
	 * slide and the channel pacing slide), and both are duplicated for every tactic, so the result keeps
	 * the template's own order — that is the order each tactic's block is built in.
	 *
	 * @param pages the deck's slides in order, from {@code presentations.get}
	 * @return the master tactic slide object ids in deck order, empty when the deck carries none
	 */
	public List<String> tacticMasterSlideIds(List<Page> pages) {
		List<String> masters = new ArrayList<>();
		Set<String> excluded = new LinkedHashSet<>(breakdownMasterSlideIds(pages).values());
		String thoughtsId = thoughtsMasterSlideId(pages);
		if (thoughtsId != null) {
			excluded.add(thoughtsId);
		}
		if (pages == null) {
			return masters;
		}
		for (Page page : pages) {
			String objectId = page.getObjectId();
			if (objectId == null || excluded.contains(objectId)) {
				continue;
			}
			if (hasMasterToken(page)) {
				masters.add(objectId);
			}
		}
		return masters;
	}

	/**
	 * Finds the deck's breakdown master slides by the marker token each one carries.
	 *
	 * @param pages the deck's slides in order, from {@code presentations.get}
	 * @return breakdown section → master slide object id, for the sections present in the deck
	 */
	public Map<BreakdownType, String> breakdownMasterSlideIds(List<Page> pages) {
		Map<BreakdownType, String> found = new LinkedHashMap<>();
		if (pages == null) {
			return found;
		}
		for (Page page : pages) {
			String objectId = page.getObjectId();
			if (objectId == null) {
				continue;
			}
			Set<String> tokens = tokensOf(page);
			for (BreakdownType type : BreakdownType.values()) {
				String marker = BREAKDOWN_MARKERS.get(type);
				if (marker != null && tokens.contains(marker) && !found.containsKey(type)) {
					found.put(type, objectId);
				}
			}
		}
		return found;
	}

	/**
	 * Finds the deck's "Thoughts on tactic performance" master slide by the marker token it carries.
	 *
	 * @param pages the deck's slides in order, from {@code presentations.get}
	 * @return the thoughts master slide object id, or {@code null} when the deck carries none
	 */
	public String thoughtsMasterSlideId(List<Page> pages) {
		if (pages == null) {
			return null;
		}
		for (Page page : pages) {
			String objectId = page.getObjectId();
			if (objectId != null && tokensOf(page).contains(THOUGHTS_MARKER)) {
				return objectId;
			}
		}
		return null;
	}

	/**
	 * Finds the dashboard slides an EOM deck must drop: the pacing-dashboard and performance-vs-plan
	 * slides whose tactic slots all sit above the campaign's tactic count. Each of those slides is drawn
	 * for a fixed block of tactics (1–7, 8–14, 15–21, 22–28), so a campaign with three tactics keeps only
	 * the first slide of each pair and the rest would ship showing raw {@code {{tactic 8 …}}} tokens.
	 *
	 * <p>A slide is judged by the lowest tactic number printed on it, which is why the channel divider —
	 * numbered from {@code {{tactic 1}}} — is never selected, and why a master slide, numbered with the
	 * variable {@code n} rather than a digit, is not considered at all.
	 *
	 * @param pages       the deck's slides in order, from {@code presentations.get}
	 * @param tacticCount number of real tactics in the campaign
	 * @return the object ids of the slides to delete, in deck order
	 */
	public List<String> surplusTacticSlideIds(List<Page> pages, int tacticCount) {
		List<String> surplus = new ArrayList<>();
		if (pages == null) {
			return surplus;
		}
		for (Page page : pages) {
			String objectId = page.getObjectId();
			if (objectId == null) {
				continue;
			}
			int lowest = lowestTacticNumber(page);
			if (lowest > 0 && lowest > tacticCount) {
				surplus.add(objectId);
			}
		}
		return surplus;
	}

	/**
	 * Finds the dashboard tables whose surplus rows have to be deleted: the tables on the last block of
	 * tactics the campaign only partly fills.
	 *
	 * <p>The pacing-dashboard and performance-vs-plan slides are drawn for fixed blocks of
	 * {@code tacticsPerBlock} tactics. A block above the campaign's tactic count is dropped whole by
	 * {@link #surplusTacticSlideIds}; the block the count lands inside keeps its slide but not all of its
	 * rows, and those rows would otherwise ship printing raw {@code {{tactic 6 …}}} tokens.
	 *
	 * <p>Only tables carrying a numbered tactic token are returned, so a decorative table on the same slide
	 * is left alone, and a master slide — numbered with the variable {@code n} — is never considered.
	 *
	 * @param pages           the deck's slides in order, from {@code presentations.get}
	 * @param tacticCount     number of real tactics in the campaign
	 * @param tacticsPerBlock tactic rows one dashboard slide draws
	 * @return the object ids of the tables to trim, in deck order; empty when the last block is full
	 */
	public List<String> partialBlockTableIds(List<Page> pages, int tacticCount, int tacticsPerBlock) {
		List<String> tables = new ArrayList<>();
		if (pages == null || tacticCount <= 0 || tacticsPerBlock <= 0
				|| tacticCount % tacticsPerBlock == 0) {
			return tables;
		}
		int blockStart = (tacticCount - 1) / tacticsPerBlock * tacticsPerBlock + 1;
		for (Page page : pages) {
			if (lowestTacticNumber(page) != blockStart) {
				continue;
			}
			tables.addAll(numberedTacticTableIds(page));
		}
		return tables;
	}

	/**
	 * Lists the tables on one slide that print numbered tactic tokens.
	 *
	 * @param page the slide to inspect
	 * @return the matching table object ids, in the order the slide carries them
	 */
	List<String> numberedTacticTableIds(Page page) {
		List<String> tables = new ArrayList<>();
		if (page == null || page.getPageElements() == null) {
			return tables;
		}
		for (PageElement element : page.getPageElements()) {
			Table table = element.getTable();
			if (table == null || element.getObjectId() == null) {
				continue;
			}
			Set<String> tokens = new LinkedHashSet<>();
			collectTableTokens(table, tokens);
			for (String token : tokens) {
				if (NUMBERED_TACTIC.matcher(token).find()) {
					tables.add(element.getObjectId());
					break;
				}
			}
		}
		return tables;
	}

	/**
	 * Collects every {@code {{…}}} token printed in a table's cells.
	 *
	 * @param table  the table to read
	 * @param tokens the accumulating token set
	 */
	void collectTableTokens(Table table, Set<String> tokens) {
		if (table.getTableRows() == null) {
			return;
		}
		for (TableRow row : table.getTableRows()) {
			if (row.getTableCells() == null) {
				continue;
			}
			for (TableCell cell : row.getTableCells()) {
				collectTokens(cell.getText(), tokens);
			}
		}
	}

	/**
	 * Tells whether a slide carries at least one master token, i.e. a token spelling the tactic variable
	 * {@code n} rather than a tactic number.
	 *
	 * @param page the slide to inspect
	 * @return {@code true} when the slide is written against the tactic variable
	 */
	boolean hasMasterToken(Page page) {
		for (String token : tokensOf(page)) {
			if (N_VARIABLE.matcher(token).find()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reads the lowest tactic number printed on a slide in a numbered token such as
	 * {@code {{tactic 8 planned imps}}}.
	 *
	 * @param page the slide to inspect
	 * @return the lowest tactic number found, or {@code 0} when the slide carries no numbered tactic token
	 */
	int lowestTacticNumber(Page page) {
		int lowest = 0;
		for (String token : tokensOf(page)) {
			Matcher matcher = NUMBERED_TACTIC.matcher(token);
			if (matcher.find()) {
				int number = Integer.parseInt(matcher.group(1));
				if (lowest == 0 || number < lowest) {
					lowest = number;
				}
			}
		}
		return lowest;
	}

	/**
	 * Collects every {@code {{…}}} token printed on a slide, across both shapes and table cells.
	 *
	 * @param page the slide to read
	 * @return the tokens in the order they appear, empty when the slide carries none
	 */
	Set<String> tokensOf(Page page) {
		Set<String> tokens = new LinkedHashSet<>();
		if (page == null || page.getPageElements() == null) {
			return tokens;
		}
		for (PageElement element : page.getPageElements()) {
			if (element.getShape() != null) {
				collectTokens(element.getShape().getText(), tokens);
			}
			Table table = element.getTable();
			if (table != null) {
				collectTableTokens(table, tokens);
			}
		}
		return tokens;
	}

	/**
	 * Adds every token found in one text container to the accumulating set.
	 *
	 * @param text   the shape or table-cell text (may be {@code null})
	 * @param tokens the accumulating token set
	 */
	void collectTokens(TextContent text, Set<String> tokens) {
		if (text == null || text.getTextElements() == null) {
			return;
		}
		StringBuilder joined = new StringBuilder();
		for (TextElement element : text.getTextElements()) {
			if (element.getTextRun() != null && element.getTextRun().getContent() != null) {
				joined.append(element.getTextRun().getContent());
			}
		}
		Matcher matcher = TOKEN.matcher(joined.toString());
		while (matcher.find()) {
			tokens.add(matcher.group());
		}
	}
}
