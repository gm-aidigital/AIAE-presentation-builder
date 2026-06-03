package com.aidigital.reportconstructor.domain.reports.repositories;

import com.aidigital.reportconstructor.domain.reports.entities.ReportJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ReportJobEntity}. Public job id = {@link ReportJobEntity#getId()}.
 */
public interface ReportJobRepository extends JpaRepository<ReportJobEntity, Long> {
}
