package com.aidigital.reportconstructor.service.version.services.impl;

import com.aidigital.reportconstructor.service.version.dto.GitVersionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitVersionServiceImplTest {

	@Test
	void shouldFallBackToUnknownWhenGitPropertiesIsAbsentFromClasspathTest() {
		// Given: the service module's test classpath has no generated git.properties
		GitVersionServiceImpl service = new GitVersionServiceImpl();

		// When:
		GitVersionInfo info = service.current();

		// Then:
		assertThat(info.shortCommitId()).isEqualTo("unknown");
		assertThat(info.commitTime()).isNull();
	}

	@Test
	void shouldCacheTheLoadedInfoAcrossRepeatedCallsTest() {
		// Given:
		GitVersionServiceImpl service = new GitVersionServiceImpl();

		// When:
		GitVersionInfo first = service.current();
		GitVersionInfo second = service.current();

		// Then: same instance returned, not re-read from disk on every call
		assertThat(first).isSameAs(second);
	}
}
