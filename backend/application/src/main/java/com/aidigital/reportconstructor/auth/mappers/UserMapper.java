package com.aidigital.reportconstructor.auth.mappers;

import com.aidigital.reportconstructor.api.v1.model.UserV1;
import com.aidigital.reportconstructor.config.ApplicationMapperConfig;
import com.aidigital.reportconstructor.service.common.security.AppUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps the service-layer {@link AppUser} to the API {@link UserV1} payload so
 * the controller stays thin and free of manual DTO assembly.
 */
@Mapper(config = ApplicationMapperConfig.class)
public interface UserMapper {

	/**
	 * Converts the caller context into the API user payload.
	 *
	 * @param appUser authenticated caller context
	 * @param isAdmin whether the caller's email is on the admin allow-list
	 * @return API user payload (identity plus the server-computed admin flag)
	 */
	@Mapping(target = "isAdmin", source = "isAdmin")
	UserV1 toUserV1(AppUser appUser, boolean isAdmin);
}
