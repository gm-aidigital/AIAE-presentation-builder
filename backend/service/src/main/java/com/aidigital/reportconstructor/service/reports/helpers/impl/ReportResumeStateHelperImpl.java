package com.aidigital.reportconstructor.service.reports.helpers.impl;

import com.aidigital.reportconstructor.service.reports.dto.CampaignData;
import com.aidigital.reportconstructor.service.reports.dto.GeneratePayload;
import com.aidigital.reportconstructor.service.reports.dto.ReportResumeState;
import com.aidigital.reportconstructor.service.reports.dto.Tactic;
import com.aidigital.reportconstructor.service.reports.helpers.ReportResumeStateHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Spring bean implementation of {@link ReportResumeStateHelper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportResumeStateHelperImpl implements ReportResumeStateHelper {

	/** The state a job carries when nothing usable was stored; never null, so callers stay simple. */
	private static final ReportResumeState EMPTY =
			new ReportResumeState(null, null, null, null, null, null, null, null);

	private final ObjectMapper objectMapper;

	@Override
	public ReportResumeState toState(GeneratePayload payload, CampaignData data) {
		if (payload == null) {
			return EMPTY;
		}
		return new ReportResumeState(
				payload.reportType(),
				payload.brief(),
				payload.changeLog(),
				payload.marketVolume(),
				payload.dateFilter(),
				payload.estimateDaypartGender(),
				payload.breakdownSelections(),
				tacticNames(data));
	}

	@Override
	public String serialize(ReportResumeState state) {
		if (state == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(state);
		} catch (Exception ex) {
			log.warn("[report] could not serialise resume state: {}", ex.getMessage());
			return null;
		}
	}

	@Override
	public ReportResumeState parse(String payloadJson) {
		if (payloadJson == null || payloadJson.isBlank()) {
			return EMPTY;
		}
		try {
			ReportResumeState state = objectMapper.readValue(payloadJson, ReportResumeState.class);
			return state == null ? EMPTY : state;
		} catch (Exception ex) {
			log.warn("[report] could not parse resume state: {}", ex.getMessage());
			return EMPTY;
		}
	}

	/**
	 * Lists the campaign's tactic names in report order.
	 *
	 * @param data the campaign data collected for the sheet build, possibly {@code null}
	 * @return the tactic names ordered by tactic number, or {@code null} when there are none
	 */
	List<String> tacticNames(CampaignData data) {
		Map<Integer, Tactic> tactics = data == null ? null : data.tactics();
		if (tactics == null || tactics.isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (Integer tacticNum : new TreeSet<>(tactics.keySet())) {
			Tactic tactic = tactics.get(tacticNum);
			names.add(tactic == null ? null : tactic.name());
		}
		return names;
	}
}
