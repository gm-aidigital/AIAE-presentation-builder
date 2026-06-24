package com.aidigital.reportconstructor.service.version.services;

import com.aidigital.reportconstructor.service.version.dto.GitVersionInfo;

/**
 * Exposes the git commit the running backend build was compiled from, so the frontend can confirm a
 * deploy actually picked up the latest commit.
 */
public interface GitVersionService {

	/**
	 * Returns the running build's git commit info.
	 *
	 * @return the short commit hash and its timestamp, or {@code "unknown"}/{@code null} when the build
	 * was produced without git metadata
	 */
	GitVersionInfo current();
}
