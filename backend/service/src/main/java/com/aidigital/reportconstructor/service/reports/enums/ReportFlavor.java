package com.aidigital.reportconstructor.service.reports.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which kind of report a run is producing, from the narrative's point of view.
 *
 * <p>The distinction exists because the two kinds tell opposite stories about the same numbers.
 * An {@link #EOC} deck reports a campaign that has finished: the copy is a verdict, written in past
 * tense, and its recommendations are things that were already done. An {@link #EOM} deck reports one
 * month of a campaign that is still running: the copy has to say where delivery stands against plan,
 * why it is pacing that way, where it lands if nothing changes, and what is being changed next.
 *
 * <p>Everything mechanical about a Claude call — the JSON schema, the character budgets, the
 * accept/retry contract — is shared between the two; only the wording differs, which is why the split
 * lives in the prompt builder rather than in the client.
 */
@Getter
@RequiredArgsConstructor
public enum ReportFlavor {

	/** End-of-campaign deck: the flight is over and the copy is a closing verdict. */
	EOC("EOC"),

	/** End-of-month deck: the flight is still live and the copy is a mid-flight status. */
	EOM("EOM");

	/** Report type code carried on the generate request and stored in {@code report_jobs.report_type_code}. */
	private final String code;
}
