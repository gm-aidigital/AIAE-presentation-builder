---
description: Backend database, Liquibase, and persistence rules.
paths:
  - "backend/db/**/*"
  - "backend/domain/src/main/java/**/*.java"
  - "backend/domain/src/test/java/**/*.java"
  - "backend/service/src/main/java/**/*.java"
---

# Backend Database Rules

- PostgreSQL only.
- New schema changes go through Liquibase under `backend/db/src/main/resources/db/changelog`.
- Do not rewrite existing applied changelogs unless the user explicitly asks for it.
- Database identifiers use Java `Long` and PostgreSQL `BIGINT`.
- Text columns use PostgreSQL `TEXT`, not `VARCHAR`.
- Entity equality/hash code must be based on the persistent identifier.
- Each entity owns one repository in `backend/domain` and one paired entity service in `backend/service/entity`.
- Repository access is centralized through the paired entity service; higher-level services do not bypass that boundary.
- Hibernate L2/query cache uses Ehcache/JCache configured at application level, not ad-hoc service-local caching.
- Hikari, JPA, Liquibase, and cache defaults are configured through application configuration, not scattered across services.
