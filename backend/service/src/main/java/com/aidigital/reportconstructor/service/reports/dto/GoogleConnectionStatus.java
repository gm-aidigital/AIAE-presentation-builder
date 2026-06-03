package com.aidigital.reportconstructor.service.reports.dto;

/**
 * Service-layer view of the caller's Google connectivity, returned by
 * {@code SheetQueryService#connectionStatus}. Mapped to the generated API
 * model by the application module.
 *
 * @param connected  whether a Google connection is available
 * @param mockMode   whether the stub (offline) provider is active
 * @param email      the caller's email
 * @param connectUrl OAuth connect URL (empty when no explicit connect is needed)
 */
public record GoogleConnectionStatus(
    boolean connected,
    boolean mockMode,
    String email,
    java.util.List<String> scopes,
    String connectUrl
) {}
