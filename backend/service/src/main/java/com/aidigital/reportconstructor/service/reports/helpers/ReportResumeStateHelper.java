package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;

/**
 * Builds and (de)serialises the state a finished SHEET run stores on its job so the report can be
 * finished in a later browser session.
 */
public interface ReportResumeStateHelper {

	/**
	 * Distils the generation payload down to the fields the slides-from-sheet step still needs.
	 *
	 * @param payload the payload the sheet was built from
	 * @param data    the campaign data collected for that build, source of the tactic names; may be
	 *                {@code null}, in which case no names are recorded
	 * @return the state to persist on the job
	 */
	ReportResumeState toState(GeneratePayload payload, CampaignData data);

	/**
	 * Serialises the state for the job's {@code payload_json} column.
	 *
	 * @param state the state to serialise, or {@code null}
	 * @return the JSON document, or {@code null} when there is nothing to store or it could not be
	 *         written — a draft that cannot be resumed is worth less than a failed report, so this
	 *         never throws into the build
	 */
	String serialize(ReportResumeState state);

	/**
	 * Reads a stored state back.
	 *
	 * @param payloadJson the job's {@code payload_json}, possibly {@code null} or unparseable (a job
	 *                    from before this was recorded, or a payload written by an older shape)
	 * @return the parsed state, or an all-null state when nothing usable is stored
	 */
	ReportResumeState parse(String payloadJson);
}
