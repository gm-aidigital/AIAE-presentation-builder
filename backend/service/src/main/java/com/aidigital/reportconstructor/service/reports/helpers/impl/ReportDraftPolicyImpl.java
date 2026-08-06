package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import com.aidigital.reportconstructor.service.reports.helpers.ReportDraftPolicy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring bean implementation of {@link ReportDraftPolicy}.
 */
@Component
public class ReportDraftPolicyImpl implements ReportDraftPolicy {

	/** Wire code of a finished job; only a finished sheet build has a workbook to resume from. */
	private static final String STATUS_DONE = "done";

	@Override
	public Set<String> consumedSheetUrls(List<ReportJobEntity> jobs) {
		Set<String> consumed = new HashSet<>();
		if (jobs == null) {
			return consumed;
		}
		for (ReportJobEntity job : jobs) {
			// Only a deck run consumes a workbook. A sheet job's own sheet_url is the workbook it
			// produced, so counting it here would make every draft look already finished.
			if (job == null || isSheetJob(job)) {
				continue;
			}
			String url = normalize(job.getSheetUrl());
			if (url != null) {
				consumed.add(url);
			}
		}
		return consumed;
	}

	@Override
	public boolean isDraft(ReportJobEntity job, Set<String> consumedSheets) {
		if (job == null || !isSheetJob(job) || job.getDismissedAt() != null) {
			return false;
		}
		if (!STATUS_DONE.equals(job.getStatus())) {
			return false;
		}
		String url = normalize(job.getSheetUrl());
		if (url == null) {
			return false;
		}
		return consumedSheets == null || !consumedSheets.contains(url);
	}

	@Override
	public boolean isListed(ReportJobEntity job, Set<String> consumedSheets) {
		if (job == null) {
			return false;
		}
		return !isSheetJob(job) || isDraft(job, consumedSheets);
	}

	/**
	 * Whether the job is an intermediate sheet build rather than a deck run. Legacy rows carry no
	 * target at all and are treated as deck runs, exactly as the history has always treated them.
	 *
	 * @param job the job to classify
	 * @return true when the job's target is the sheet build
	 */
	boolean isSheetJob(ReportJobEntity job) {
		return GenerationTarget.SHEET.name().equals(job.getTarget());
	}

	/**
	 * Trims a stored sheet URL to the form the two jobs are compared on.
	 *
	 * @param url the stored URL, possibly {@code null} or blank
	 * @return the trimmed URL, or {@code null} when there is nothing to compare
	 */
	String normalize(String url) {
		if (url == null) {
			return null;
		}
		String trimmed = url.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
