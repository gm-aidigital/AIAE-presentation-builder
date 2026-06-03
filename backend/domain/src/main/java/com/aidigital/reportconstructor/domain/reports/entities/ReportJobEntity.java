package com.aidigital.reportconstructor.domain.reports.entities;

import com.aidigital.reportconstructor.domain.common.entities.IdAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Persisted progress and outcome of a single report-generation run.
 * Public job id is the surrogate {@link #getId()} (int64).
 */
@Entity
@Table(name = "report_jobs")
@Getter
@Setter
public class ReportJobEntity extends IdAwareEntity {

    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "step", nullable = false)
    private Integer step;

    @Column(name = "total", nullable = false)
    private Integer total;

    @Column(name = "label")
    private String label;

    @Column(name = "report_type_code")
    private String reportTypeCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings_json", columnDefinition = "jsonb")
    private String warningsJson;

    @Column(name = "slide_url")
    private String slideUrl;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
