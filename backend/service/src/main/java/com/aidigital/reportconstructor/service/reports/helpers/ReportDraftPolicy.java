package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;

import java.util.List;
import java.util.Set;

/**
 * Decides which of an owner's jobs are resumable drafts.
 *
 * <p>A SHEET job is an intermediate step of a report, not a report — but between the two steps the
 * user leaves to fill the generated workbook by hand, and until they come back that half-finished
 * run is the only thing standing between them and repeating the whole wizard. It is offered as a
 * draft while it is still worth resuming, and hidden once it is not.
 */
public interface ReportDraftPolicy {

	/**
	 * Collects the source-sheet URLs that have already been turned into a deck.
	 *
	 * @param jobs the owner's jobs
	 * @return the sheet URLs a deck run consumed (empty when none did)
	 */
	Set<String> consumedSheetUrls(List<ReportJobEntity> jobs);

	/**
	 * Whether the job is a draft the owner can still resume: a finished sheet build, not dismissed,
	 * whose workbook no deck has been generated from yet.
	 *
	 * @param job           the job to classify
	 * @param consumedSheets the sheet URLs already turned into a deck, from
	 *                       {@link #consumedSheetUrls(List)}
	 * @return true when the job should be offered as a resumable draft
	 */
	boolean isDraft(ReportJobEntity job, Set<String> consumedSheets);

	/**
	 * Whether the job belongs on the owner's "My reports" list at all: every deck run, plus the
	 * sheet runs that are resumable drafts.
	 *
	 * @param job           the job to classify
	 * @param consumedSheets the sheet URLs already turned into a deck
	 * @return true when the job should be listed
	 */
	boolean isListed(ReportJobEntity job, Set<String> consumedSheets);
}
