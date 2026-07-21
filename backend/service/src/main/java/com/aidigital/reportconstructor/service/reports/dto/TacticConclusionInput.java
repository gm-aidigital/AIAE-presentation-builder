package com.aidigital.reportconstructor.service.reports.dto;

/**
 * One tactic's full context for the Step-2 combined per-tactic conclusions call: the tactic's number
 * plus whichever breakdown-section inputs the tactic toggled on. A section left {@code null} was not
 * enabled for this tactic and must not be requested, so the combined prompt only ever asks for copy the
 * user opted into. The tactic's performance metrics for the overview are not carried here — they are read
 * from the shared {@link CampaignData} the call also receives, keyed by {@link #tacticNum()}.
 *
 * @param tacticNum the 1-based tactic number, used to route the reply back to the tactic and its slides
 * @param publisher the tactic's "Top Publishers" input, or {@code null} when the section is off
 * @param creative  the tactic's "Creative analysis" input, or {@code null} when the section is off
 * @param geo       the tactic's "Geo analysis" input, or {@code null} when the section is off
 * @param audience  the tactic's "Audience analysis" input, or {@code null} when the section is off
 * @param device    the tactic's "Device breakdown" input, or {@code null} when the section is off
 */
public record TacticConclusionInput(
		int tacticNum,
		PublisherObservationInput publisher,
		CreativeTakeawayInput creative,
		GeoInsightInput geo,
		AudienceInsightInput audience,
		DeviceInsightInput device
) {
}
