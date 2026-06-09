package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Service-layer view of the caller's Google connectivity, returned by
 * {@code SheetQueryService#connectionStatus}. Mapped to the generated API
 * model by the application module.
 *
 * @param connected  whether a usable Google connection (real or mock) is currently available for the caller
 * @param mockMode   whether the stub (offline) Google provider is active instead of a live OAuth-backed connection
 * @param email      the connected Google account's email address, or empty when no account is linked
 * @param scopes     the OAuth scopes granted to the connection (e.g. Sheets/Slides access), empty when none
 * @param connectUrl the OAuth connect URL the caller should visit to authZ, empty when no explicit connect is needed
 */
public record GoogleConnectionStatus(
    boolean connected,
    boolean mockMode,
    String email,
    java.util.List<String> scopes,
    String connectUrl
) {
	// required
}
