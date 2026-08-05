package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.service.reports.dto.GenerationTarget;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a report job counts as a standalone report in the admin dashboard's
 * report-volume figures.
 *
 * <p>The slide-deck flow (EOC/EOM) runs in two steps: step 1 builds a Google Sheet the user
 * reviews, step 2 turns that reviewed sheet into the deck. Each step is persisted as its own
 * {@code report_jobs} row, so counting every row as a report double-counts one campaign. The
 * intermediate review sheet — a {@link GenerationTarget#SHEET} job whose report type renders a
 * deck — is therefore not a report in its own right; the deck it feeds is. A {@code SHEET} job of
 * any other type (a standalone spreadsheet deliverable) is a report and still counts.
 *
 * <p>Only report <em>counts</em> use this policy. Token spend and the failures list keep reading
 * every job, so an intermediate step's cost and any error it hit stay visible.
 */
@Component
public class ReportCountPolicy {

	/** Wire code of the {@link GenerationTarget#SHEET} target as stored on the job's {@code target} column. */
	private static final String SHEET_TARGET = GenerationTarget.SHEET.name();

	/**
	 * Report-type codes that render a slide deck and whose {@code SHEET} step is only an intermediate
	 * review artifact rather than a deliverable in its own right.
	 */
	private static final Set<String> SLIDE_DECK_TYPES = Set.of("EOC", "EOM");

	/**
	 * Tells whether a job is the intermediate review sheet of a slide-deck flow rather than a report.
	 *
	 * @param job the persisted report job
	 * @return true when the job is a slide-deck flow's intermediate {@code SHEET} step
	 */
	public boolean isIntermediateSheet(ReportJobEntity job) {
		return isIntermediateSheet(job.getTarget(), job.getReportTypeCode());
	}

	/**
	 * Tells whether a (target, report type) pair describes a slide-deck flow's intermediate review
	 * sheet rather than a report.
	 *
	 * <p>The pair form exists for the {@code usage_daily} rollup, whose rows are groups of jobs rather
	 * than jobs. Keeping both forms on this one class is the point: the rollup stores the two columns
	 * and asks the same question of them, so the rule stays in one place instead of being duplicated
	 * into SQL.
	 *
	 * @param target         the job's generation target, possibly {@code null} or a placeholder
	 * @param reportTypeCode the job's report type code, possibly {@code null}
	 * @return true when the pair is a slide-deck flow's intermediate {@code SHEET} step
	 */
	public boolean isIntermediateSheet(String target, String reportTypeCode) {
		return SHEET_TARGET.equals(target) && isSlideDeckType(reportTypeCode);
	}

	/**
	 * Tells whether a (target, report type) pair counts as a standalone report.
	 *
	 * @param target         the job's generation target, possibly {@code null} or a placeholder
	 * @param reportTypeCode the job's report type code, possibly {@code null}
	 * @return true unless the pair is a slide-deck flow's intermediate sheet step
	 */
	public boolean isCountableReport(String target, String reportTypeCode) {
		return !isIntermediateSheet(target, reportTypeCode);
	}

	/**
	 * Tells whether a job counts as a standalone report in the dashboard's report-volume figures.
	 *
	 * @param job the persisted report job
	 * @return true unless the job is a slide-deck flow's intermediate sheet step
	 */
	public boolean isCountableReport(ReportJobEntity job) {
		return !isIntermediateSheet(job);
	}

	/**
	 * Normalizes a report-type code and tests it against the slide-deck set.
	 *
	 * @param reportTypeCode the raw {@code report_type_code}, possibly {@code null}
	 * @return true when the code names a slide-deck report type
	 */
	boolean isSlideDeckType(String reportTypeCode) {
		return reportTypeCode != null
				&& SLIDE_DECK_TYPES.contains(reportTypeCode.trim().toUpperCase(Locale.ROOT));
	}
}
