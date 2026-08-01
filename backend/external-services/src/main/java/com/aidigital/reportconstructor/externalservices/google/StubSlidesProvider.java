package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.dto.BreakdownType;
import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Deterministic Slides provider — the only candidate when no {@code @Primary}
 * real Slides bean is registered (i.e. when {@code GOOGLE_SERVICE_ACCOUNT_JSON}
 * is unset and {@link RealSlidesProvider} stays conditional-excluded).
 *
 * <p>Fabricates the template URL with the job-id suffix so the UI flow
 * remains end-to-end runnable without Google access.
 */
@Component
@RequiredArgsConstructor
public class StubSlidesProvider implements SlidesProvider {

	private final GoogleProperties props;

	@Override
	public boolean isLive() {
		return false;
	}

	@Override
	public String createDeck(
			String jobId, String fileName, Map<String, String> placeholderMap, String reportType,
			String userGoogleAccessToken) {
		String eomTemplateId = props.getEomSlidesTemplateId();
		String templateId = "EOM".equals(reportType) && eomTemplateId != null && !eomTemplateId.isBlank()
				? eomTemplateId : props.getSlidesTemplateId();
		return "https://docs.google.com/presentation/d/" + templateId + "/edit?stub=" + jobId;
	}

	@Override
	public void trimTactics(String presentationId, int tacticCount, String userGoogleAccessToken) {
		// No-op: the stub never clones a real deck, so there are no slides to trim.
	}

	@Override
	public void addBreakdownSlides(
			String presentationId, Map<Integer, Set<BreakdownType>> enabledByTactic,
			Map<String, String> breakdownValues, String userGoogleAccessToken) {
		// No-op: the stub never clones a real deck, so there are no master slides to duplicate.
	}

	@Override
	public void deleteMasterSlides(String presentationId, String userGoogleAccessToken) {
		// No-op: the stub never clones a real deck, so there are no master slides to delete.
	}

	@Override
	public void deleteReportTypeSlides(String presentationId, String reportType, String userGoogleAccessToken) {
		// No-op: the stub never clones a real deck, so there are no report-type slides to delete.
	}
}
