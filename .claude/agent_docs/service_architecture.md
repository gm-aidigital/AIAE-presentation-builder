# Service Architecture

## Backend services

These rules apply to services under `backend/service`.

## Core boundary rule

Use this rule everywhere:

```text
1 entity = 1 repository = 1 service to work with that entity
```

That means:

- `HubUserRepository` is used through `HubUserService`.
- `HubRoleRepository` is used through `HubRoleService`.
- `HubRoleAssignmentRepository` is used through `HubRoleAssignmentService`.
- `HubScopeTypeRepository` is used through `HubScopeTypeService`.
- `HubTeamRepository` is used through `HubTeamService`.

## What orchestrators may do

Cross-entity services such as `RbacQueryServiceImpl`, `RbacAdministrationServiceImpl`, `CurrentUserServiceImpl`, or `AgencyClientServiceImpl` may coordinate:

- entity services
- validators
- mappers
- external-service clients
- exception factories / error enums

They must not inject repositories directly.

## Why this rule exists

- Repository ownership stays obvious.
- Locking and query semantics stay centralized per entity.
- RBAC or other orchestration services stay at the business layer instead of becoming ad-hoc data-access hubs.
- Unit tests stay cleaner because cross-entity behavior mocks service contracts, not repositories.

## Specific example

`RbacQueryServiceImpl` must read user data through `HubUserService` and assignment data through `HubRoleAssignmentService`. It must not inject `HubUserRepository` or `HubRoleAssignmentRepository` directly.

## Additional backend service rules

- Transactions belong in the service layer.
- Outbound HTTP/SDK calls belong in `backend/external-services`, never in entity services or controllers.
- Production beans expose no `private` methods. Extract validators, policies, assemblers, or helpers (each unit-testable) instead of hiding logic in private methods, and keep collaborator methods package-private so they are spyable. Only `private static final` constants and `private final` fields stay private.
- Data carried between collaborators (resolved rows, command results, view models) is a top-level type in the `model` package, never a nested record/class.
- MapStruct mappers stay narrow: entity-to-model in `service`, model-to-contract in `application`.
