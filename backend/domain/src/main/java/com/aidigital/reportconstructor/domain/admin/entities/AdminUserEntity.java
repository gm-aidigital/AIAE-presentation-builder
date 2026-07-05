package com.aidigital.reportconstructor.domain.admin.entities;

import com.aidigital.reportconstructor.domain.common.entities.IdAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A UI-managed admin grant. Root admins from {@code app.admin.emails} are not stored
 * here; this table only holds admins added through the admin UI.
 */
@Entity
@Table(name = "admin_users")
@Getter
@Setter
public class AdminUserEntity extends IdAwareEntity {

	@Column(name = "email", nullable = false, unique = true)
	private String email;

	@Column(name = "added_by_email")
	private String addedByEmail;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
