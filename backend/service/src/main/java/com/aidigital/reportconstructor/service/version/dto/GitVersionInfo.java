package com.aidigital.reportconstructor.service.version.dto;

/**
 * Service-layer view of the running backend build's git provenance, returned by
 * {@code GitVersionService#current}. Mapped to the generated API model by the application module.
 *
 * @param shortCommitId short git commit hash of the running build, or {@code "unknown"} when the build was
 *                       produced without git metadata
 * @param commitTime     ISO-8601 timestamp of {@code shortCommitId}'s commit, or {@code null} when unavailable
 */
public record GitVersionInfo(String shortCommitId, String commitTime) {

}
