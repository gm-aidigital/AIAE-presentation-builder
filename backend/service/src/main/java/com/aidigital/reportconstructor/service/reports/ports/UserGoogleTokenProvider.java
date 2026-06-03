package com.aidigital.reportconstructor.service.reports.ports;

/**
 * Fetches a signed-in user's Google OAuth access token (Clerk-brokered) so deck
 * generation can run in the user's Drive. Implementations live in
 * {@code external-services}; the service module depends only on this port.
 */
public interface UserGoogleTokenProvider {

    /**
     * @param userId Clerk user id ({@code sub} claim)
     * @return Google OAuth access token, or {@code null} when unavailable (non-fatal)
     */
    String googleAccessToken(String userId);
}
