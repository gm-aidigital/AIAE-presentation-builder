package com.aidigital.reportconstructor.domain.reports.repositories;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link ReportJobEntity}. Public job id = {@link ReportJobEntity#getId()}.
 */
public interface ReportJobRepository extends JpaRepository<ReportJobEntity, Long> {

	/**
	 * Lists a single user's jobs, newest first, for the "My reports" history screen.
	 *
	 * @param ownerUserId internal owner id whose jobs are returned
	 * @return the owner's jobs ordered by creation time descending
	 */
	List<ReportJobEntity> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

	/**
	 * Lists every job, newest first, for team-wide admin aggregation.
	 *
	 * @return all jobs ordered by creation time descending
	 */
	List<ReportJobEntity> findAllByOrderByCreatedAtDesc();
}
