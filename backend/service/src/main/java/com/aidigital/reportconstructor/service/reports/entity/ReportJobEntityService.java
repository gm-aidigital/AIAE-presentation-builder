package com.aidigital.reportconstructor.service.reports.entity;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import com.aidigital.reportconstructor.domain.reports.repositories.ReportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Paired entity service for {@link ReportJobEntity}.
 * The sole bean allowed to inject {@link ReportJobRepository}.
 */
@Service
@RequiredArgsConstructor
public class ReportJobEntityService {

	private final ReportJobRepository reportJobRepository;

	/**
	 * Persists (insert or update) a report job and returns the managed entity.
	 *
	 * @param entity entity to save
	 * @return saved entity with any generated fields populated
	 */
	public ReportJobEntity save(ReportJobEntity entity) {
		return reportJobRepository.save(entity);
	}

	/**
	 * Looks up a report job by its surrogate id.
	 *
	 * @param id surrogate identifier of the job
	 * @return an {@link Optional} containing the job, or empty when not found
	 */
	public Optional<ReportJobEntity> findById(Long id) {
		return reportJobRepository.findById(id);
	}
}
