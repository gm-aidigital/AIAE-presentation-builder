package com.aidigital.reportconstructor.service.admin.services;

import com.aidigital.reportconstructor.service.admin.dto.AdminEntry;

import java.util.List;

/**
 * Admin management: list, grant, and revoke admin access by email. Every operation
 * requires the caller to already be an admin.
 */
public interface AdminManagementService {

	/**
	 * Lists all admins — config (root) grants first, then UI-managed grants newest first.
	 *
	 * @param callerEmail email of the requesting admin
	 * @return the current admins
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException {@code C004} when the caller is not an admin
	 */
	List<AdminEntry> listAdmins(String callerEmail);

	/**
	 * Grants managed admin access to an email and returns the updated list.
	 *
	 * @param callerEmail email of the requesting admin
	 * @param email       email to grant admin access to
	 * @return the admins after the grant
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException {@code C004} when the caller is not an
	 *                                                                           admin, {@code C002} when the email is invalid
	 */
	List<AdminEntry> addAdmin(String callerEmail, String email);

	/**
	 * Revokes a UI-managed admin grant and returns the updated list.
	 *
	 * @param callerEmail email of the requesting admin
	 * @param email       email to revoke
	 * @return the admins after the revoke
	 * @throws com.aidigital.reportconstructor.service.common.error.AppException {@code C004} when the caller is not an
	 *                                                                           admin, {@code C002} when trying to remove a
	 *                                                                           config (root) admin, {@code C001} when no
	 *                                                                           managed grant exists
	 */
	List<AdminEntry> removeAdmin(String callerEmail, String email);
}
