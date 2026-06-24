package com.aidigital.reportconstructor.service.version.services.impl;

import com.aidigital.reportconstructor.service.version.dto.GitVersionInfo;
import com.aidigital.reportconstructor.service.version.services.GitVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Properties;

/**
 * Reads the build-time generated {@code git.properties} (written by git-commit-id-maven-plugin with
 * {@code prefix=git-commit-id}) to expose the running build's git commit. Read once at startup and
 * cached, since the running JAR's commit never changes during its own lifetime.
 */
@Slf4j
@Service
public class GitVersionServiceImpl implements GitVersionService {

	private static final String UNKNOWN = "unknown";
	private static final String COMMIT_ID_KEY = "git-commit-id.commit.id.abbrev";
	private static final String COMMIT_TIME_KEY = "git-commit-id.commit.time";

	private final GitVersionInfo cached;

	/**
	 * Loads and caches the git version info from the classpath {@code git.properties} resource.
	 */
	public GitVersionServiceImpl() {
		this.cached = loadVersionInfo();
	}

	@Override
	public GitVersionInfo current() {
		return cached;
	}

	/**
	 * Reads {@code classpath:git.properties} and extracts the short commit hash and commit timestamp,
	 * falling back to {@code "unknown"}/{@code null} when the resource is missing or unreadable (e.g. a
	 * build produced without a {@code .git} directory).
	 *
	 * @return the resolved git version info
	 */
	GitVersionInfo loadVersionInfo() {
		try {
			Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("git.properties"));
			String commitId = props.getProperty(COMMIT_ID_KEY, UNKNOWN);
			String commitTime = props.getProperty(COMMIT_TIME_KEY);
			return new GitVersionInfo(commitId, commitTime);
		} catch (IOException ex) {
			log.warn("git.properties not found on classpath; reporting commit id as '{}'", UNKNOWN);
			return new GitVersionInfo(UNKNOWN, null);
		}
	}
}
