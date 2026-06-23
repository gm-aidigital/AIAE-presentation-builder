# Database Schema

## Backend database baseline

- PostgreSQL only.
- Schema changes go through Liquibase in `backend/db/src/main/resources/db/changelog`.
- Add new changelog files; do not rewrite existing applied changelogs.

## Backend entity conventions

- Database identifiers use `Long` on the Java side.
- Textual PostgreSQL columns should use `TEXT`, not `VARCHAR`.
- Business code dimensions should be represented by Java enums and lookup data, not scattered static code bags.
- Entity equality/hash code should be based on the persistent identifier.
- Repository interfaces live only in the `backend/domain` module.

## Backend repository rules

- Each entity owns its repository.
- Repository access is wrapped by the paired entity service in `backend/service/entity`.
- Cross-entity business services must not inject repositories directly.
- Non-trivial queries belong to the repository of the entity they primarily load or lock, then are exposed through that entity service.

## Backend query testing

- Use `@DataJpaTest` with embedded PostgreSQL for custom queries.
- Use `@Sql` scripts to create realistic query fixtures.
- Disable infrastructure that obscures query behavior when needed, for example Liquibase or second-level cache in focused repository tests.

## Backend cache and JPA baseline

- Hibernate second-level and query cache use Ehcache/JCache.
- Cache configuration is application-level configuration, not ad-hoc service-local caching.
- `backend/application/src/main/resources/application.yml` is the source of truth for Hikari, JPA, Liquibase, and cache defaults.
- Keep Hikari, JPA, cache, and Liquibase tuning in application configuration rather than service code.
