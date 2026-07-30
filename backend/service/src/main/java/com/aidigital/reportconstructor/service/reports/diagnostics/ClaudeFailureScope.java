package com.aidigital.reportconstructor.service.reports.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The reasons Claude replies were rejected during one run, collected for the "Report ready" card.
 *
 * <p>A rejected reply is otherwise only visible in the server log, which on a hosted deployment is the
 * one place the person who ran the report cannot look. Carrying the reasons out to the job's warnings
 * puts the cause — the parse failure, the wrong item count, the blank field — next to the blank slide it
 * produced.
 *
 * <p>One scope is created per run and handed to the worker threads its Claude calls fan out to, so the
 * entry list is synchronized. Both the number of entries and their length are capped: the list is
 * serialized into the job's warnings column and rendered as a bullet list, so a run that fails on every
 * section must not turn into an unbounded blob.
 */
public class ClaudeFailureScope {

	/** Entries kept per run; a run that fails this often needs the first few reasons, not all of them. */
	private static final int MAX_ENTRIES = 40;

	/** Characters kept per entry, enough for a reply head that shows what broke the parse. */
	private static final int MAX_ENTRY_LENGTH = 600;

	private final List<String> entries = Collections.synchronizedList(new ArrayList<>());

	/**
	 * Records one rejection reason, trimming it to {@link #MAX_ENTRY_LENGTH} and ignoring anything past
	 * {@link #MAX_ENTRIES}. Blank text is dropped so an empty bullet never reaches the card.
	 *
	 * @param entry the human-readable reason, already carrying whatever call it belongs to
	 */
	public void add(String entry) {
		if (entry == null || entry.isBlank()) {
			return;
		}
		String trimmed = entry.length() <= MAX_ENTRY_LENGTH
				? entry
				: entry.substring(0, MAX_ENTRY_LENGTH) + "…";
		synchronized (entries) {
			if (entries.size() < MAX_ENTRIES) {
				entries.add(trimmed);
			}
		}
	}

	/**
	 * Takes the reasons recorded so far as an immutable snapshot, for adding to the run's warnings.
	 *
	 * @return the recorded reasons in the order they occurred; empty when nothing was rejected
	 */
	public List<String> snapshot() {
		synchronized (entries) {
			return List.copyOf(entries);
		}
	}
}
