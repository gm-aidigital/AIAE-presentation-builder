package com.aidigital.reportconstructor.domain.admin.repositories;

import com.aidigital.reportconstructor.domain.admin.entities.AdminUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link AdminUserEntity} (UI-managed admin grants).
 */
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {

	/**
	 * Finds a managed admin by email, case-insensitively.
	 *
	 * @param email email to look up
	 * @return the grant if present
	 */
	Optional<AdminUserEntity> findByEmailIgnoreCase(String email);

	/**
	 * Tells whether a managed admin grant exists for the email, case-insensitively.
	 *
	 * @param email email to check
	 * @return true when a managed grant exists
	 */
	boolean existsByEmailIgnoreCase(String email);

	/**
	 * Lists all managed admin grants, newest first.
	 *
	 * @return managed grants ordered by creation time descending
	 */
	List<AdminUserEntity> findAllByOrderByCreatedAtDesc();
}
