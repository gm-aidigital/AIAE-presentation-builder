---
description: Always-on AIAE Presentation Builder backend rules.
---

# AIAE Presentation Builder Backend Hard Rules

- Backend stack is fixed: Java 21, Spring Boot 3.x, Maven multi-module, PostgreSQL, Liquibase.
- Backend production packages stay under `com.aidigital.operationalhub.*`.
- Backend controllers implement generated OpenAPI interfaces and stay thin.
- Backend controllers and orchestration services must not inject JPA repositories directly.
- Every backend JPA entity has one repository in `domain` and one paired entity service in `service/entity`.
- Follow `1 entity = 1 repository = 1 service to work with that entity`.
- Outbound integrations live in `backend/external-services`, not in backend `application` or `service` orchestration classes.
- Use `@ConfigurationProperties` for backend configuration; do not use `@Value`.
- Do not introduce static methods on backend services or Spring beans.
- Use Lombok for backend boilerplate and constructor injection.
- Replace magic strings and numbers with constants or enums.
- Business codes such as RBAC role/scope codes must be enums with fields, not static code bags.
- No nested data types in production code: records, DTOs, data-holding classes, and enums are declared as top-level types in the module's `model` (or `enums`) package, never nested inside another class. The only allowed nested types are `@ConfigurationProperties` sub-groups (Spring binds nested property classes).
- No `private` methods in production beans/services: methods are package-private or wider so collaborators stay spyable/mockable in unit tests. Extract non-trivial logic into a collaborator (validator/policy/assembler/helper) instead of a private method. Only `private static final` constants and `private final` fields stay private.
- Every handwritten production method requires JavaDoc, regardless of visibility. Generated sources, Lombok-generated members, and `@Override` methods that inherit their contract are exempt.
- New logging should follow structured JSON logging expectations.
- Do not edit generated OpenAPI sources under `backend/application/target/generated-sources`.
