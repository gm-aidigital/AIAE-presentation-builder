package com.aidigital.reportconstructor.service.reports.dto;

/**
 * A resumable draft handed back to the constructor so it can re-enter the wizard at the review
 * step: the workbook to review plus the state the earlier session was carrying.
 *
 * @param jobId        id of the SHEET job that produced the workbook
 * @param sheetUrl     the generated Google Sheet the deck will be built from
 * @param mediaPlanUrl Media Plan source sheet the original session connected, or {@code null}
 * @param elevateUrl   Elevate source sheet the original session connected, or {@code null}
 * @param state        the persisted wizard state; never {@code null}, but every field inside it may
 *                     be absent for a draft written before this was recorded
 */
public record ReportResume(
		Long jobId,
		String sheetUrl,
		String mediaPlanUrl,
		String elevateUrl,
		ReportResumeState state) {
}
