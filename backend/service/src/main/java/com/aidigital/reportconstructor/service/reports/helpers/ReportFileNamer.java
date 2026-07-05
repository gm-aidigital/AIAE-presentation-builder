package com.aidigital.reportconstructor.service.reports.helpers;

/**
 * Builds the human-readable Drive file name for a generated report artifact
 * (Slides deck or Sheets workbook), following the
 * {@code <type>_<client>_<date>_<time>_<user>} convention where the date/time
 * is stamped in the server's local time zone.
 */
public interface ReportFileNamer {

	/**
	 * Builds the report file name from the report type, client name and creating
	 * user, stamping the current server-local date and time.
	 *
	 * @param reportType report template code (e.g. {@code EOC}); blank falls back to {@code REPORT}
	 * @param clientName resolved client name; blank falls back to {@code report}
	 * @param userEmail  email of the user who triggered the build; the local part (before {@code @})
	 *                   is used, and blank falls back to {@code unknown}
	 * @return the assembled file name, e.g. {@code EOC_Acme_2026-07-05_14-30_a.kuzmin}
	 */
	String buildFileName(String reportType, String clientName, String userEmail);
}
