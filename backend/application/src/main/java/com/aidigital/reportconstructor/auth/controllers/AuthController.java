package com.aidigital.reportconstructor.auth.controllers;

import com.aidigital.reportconstructor.api.v1.AuthApi;
import com.aidigital.reportconstructor.api.v1.model.UserV1;
import com.aidigital.reportconstructor.auth.mappers.UserMapper;
import com.aidigital.reportconstructor.security.AppUserFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoint — returns the authenticated user. Implements the generated
 * {@code AuthApi} from {@code openapi.yaml}. Clerk SSO is the only auth mode:
 * authentication is enforced by the Bearer-JWT security chain before this
 * method runs; an unauthenticated request returns 401 before reaching here.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

	private final AppUserFactory appUserFactory;
	private final UserMapper userMapper;

	/**
	 * Returns the authenticated user payload built from the JWT in the
	 * security context.
	 *
	 * @return 200 with {@link UserV1} populated from the JWT claims.
	 */
	@Override
	public ResponseEntity<UserV1> getCurrentUser() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		return ResponseEntity.ok(userMapper.toUserV1(appUserFactory.from(auth)));
	}
}
