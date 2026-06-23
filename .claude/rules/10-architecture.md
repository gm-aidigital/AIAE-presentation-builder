---
description: Backend layering and entity-service boundary rules.
paths:
  - "backend/application/src/main/java/**/*.java"
  - "backend/service/src/main/java/**/*.java"
  - "backend/domain/src/main/java/**/*.java"
---

# Backend Architecture Rules

- Follow the rule `1 entity = 1 repository = 1 service to work with that entity`.
- Only the paired entity service implementation may inject that entity's repository.
- Cross-entity services and query services must depend on entity services, not repositories.
- `RbacQueryServiceImpl`-style orchestration belongs in the service layer and must not become a repository hub.
- Controllers never inject repositories.
- Outbound HTTP/SDK integrations live in `backend/external-services` only.
- If a new query primarily loads or locks one entity, place it in that entity repository and expose it through that entity service.
- If service logic grows complex, extract validator/policy/helper collaborators instead of adding another private-method cluster.
