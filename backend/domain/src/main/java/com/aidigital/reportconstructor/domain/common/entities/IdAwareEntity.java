package com.aidigital.reportconstructor.domain.common.entities;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

/**
 * Base JPA entity with ID-only {@code equals}/{@code hashCode} semantics.
 */
@Setter
@Getter
@MappedSuperclass
public abstract class IdAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	@Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        IdAwareEntity that = (IdAwareEntity) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
