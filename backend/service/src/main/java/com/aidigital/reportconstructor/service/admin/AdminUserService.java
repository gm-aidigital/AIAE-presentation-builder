package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;

import java.util.List;

/**
 * Entity service for {@link AdminUserEntity} — the single boundary onto the
 * {@code admin_users} repository (UI-managed admin grants).
 */
public interface AdminUserService {

	/**
	 * Tells whether a managed admin grant exists for the email, case-insensitively.
	 *
	 * @param email email to check (any case)
	 * @return true when a managed grant exists
	 */
	boolean exists(String email);

	/**
	 * Lists all managed admin grants, newest first.
	 *
	 * @return managed grants ordered newest first
	 */
	List<AdminUserEntity> listAll();

	/**
	 * Grants managed admin access to an email, idempotently.
	 *
	 * @param email       email to grant (stored lowercased)
	 * @param addedByEmail email of the admin performing the grant
	 * @return the persisted (or pre-existing) grant
	 */
	AdminUserEntity add(String email, String addedByEmail);

	/**
	 * Revokes a managed admin grant by email, case-insensitively.
	 *
	 * @param email email to revoke
	 * @return true when a grant was removed, false when none existed
	 */
	boolean removeByEmail(String email);
}
