package com.aidigital.reportconstructor.version.mappers;

import com.aidigital.reportconstructor.api.v1.model.VersionV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.version.dto.GitVersionInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps the service-layer git version info to its V1 API DTO.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface VersionApiMapper {

	/**
	 * Converts the running build's git version info into its V1 DTO.
	 *
	 * @param info the service git version info
	 * @return the V1 version DTO
	 */
	@Mapping(target = "commitId", source = "shortCommitId")
	VersionV1 toVersion(GitVersionInfo info);
}
