package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.reports.ports.SlidesProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

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
    public String createDeck(String jobId, Map<String, String> placeholderMap, String userGoogleAccessToken) {
        return "https://docs.google.com/presentation/d/" + props.getSlidesTemplateId() + "/edit?stub=" + jobId;
    }

    @Override
    public void trimTactics(String presentationId, int tacticCount, String userGoogleAccessToken) {
        // No-op: the stub never clones a real deck, so there are no slides to trim.
    }
}
