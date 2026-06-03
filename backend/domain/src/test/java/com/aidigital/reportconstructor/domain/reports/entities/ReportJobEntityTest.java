package com.aidigital.reportconstructor.domain.reports.entities;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportJobEntityTest {

    @Test
    void shouldExposeOwnerAndStatusFields() {
        ReportJobEntity job = new ReportJobEntity();
        job.setOwnerUserId("user_abc");
        job.setStatus("queued");
        job.setStep(0);
        job.setTotal(7);
        job.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        job.setUpdatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        job.setLabel("Queued…");
        job.setReportTypeCode("EOC");
        job.setPayloadJson("{}");
        job.setWarningsJson("[]");
        job.setSlideUrl("https://slides.example/edit");
        job.setErrorMessage("");

        assertThat(job.getOwnerUserId()).isEqualTo("user_abc");
        assertThat(job.getStatus()).isEqualTo("queued");
        assertThat(job.getTotal()).isEqualTo(7);
        assertThat(job.getLabel()).isEqualTo("Queued…");
        assertThat(job.getSlideUrl()).contains("slides.example");
        assertThat(job.getStep()).isZero();
        assertThat(job.getPayloadJson()).isEqualTo("{}");
    }

    @Test
    void shouldRejectEqualsWithNullOrUnrelatedType() {
        ReportJobEntity job = new ReportJobEntity();
        job.setId(9L);
        assertThat(job).isNotEqualTo(null);
        assertThat(job).isNotEqualTo("other");
    }

    @Test
    void shouldInheritIdAwareEqualitySemantics() {
        ReportJobEntity left = new ReportJobEntity();
        left.setId(1L);
        ReportJobEntity right = new ReportJobEntity();
        right.setId(1L);
        assertThat(left).isEqualTo(right);
        assertThat(left.hashCode()).isEqualTo(right.hashCode());
    }

    @Test
    void shouldNotEqualDifferentIdsOrTransientEntities() {
        ReportJobEntity a = new ReportJobEntity();
        ReportJobEntity b = new ReportJobEntity();
        assertThat(a).isNotEqualTo(b);

        a.setId(1L);
        b.setId(2L);
        assertThat(a).isNotEqualTo(b);
    }
}
