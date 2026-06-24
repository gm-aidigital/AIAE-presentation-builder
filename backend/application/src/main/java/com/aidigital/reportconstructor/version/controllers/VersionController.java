package com.aidigital.reportconstructor.version.controllers;

import com.aidigital.reportconstructor.api.v1.SystemApi;
import com.aidigital.reportconstructor.api.v1.model.VersionV1;
import com.aidigital.reportconstructor.service.version.services.GitVersionService;
import com.aidigital.reportconstructor.version.mappers.VersionApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the running backend build's git commit, so the frontend can confirm a
 * deploy picked up the latest commit.
 */
@RestController
@RequiredArgsConstructor
public class VersionController implements SystemApi {

	private final GitVersionService gitVersionService;
	private final VersionApiMapper mapper;

	@Override
	public ResponseEntity<VersionV1> getVersion() {
		return ResponseEntity.ok(mapper.toVersion(gitVersionService.current()));
	}
}
