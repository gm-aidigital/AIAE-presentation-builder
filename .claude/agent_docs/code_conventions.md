# Code Conventions

## Backend conventions

These conventions apply to Java backend code under `backend/`.

## General

- Use Java 21 language level.
- Prefer Lombok for boilerplate and constructor injection.
- Spring configuration must use `@ConfigurationProperties`; do not use `@Value`.
- Keep Spring beans instance-based. Do not introduce static methods on services or other Spring beans.
- Services must emit structured logs in JSON format when they log business or technical events.

## Controllers and mapping

- Controllers implement generated OpenAPI `*Api` interfaces.
- Controllers stay thin: request extraction, authorization call, service call, response mapping.
- Do not move business orchestration or repository access into controllers.
- Keep service-to-contract mapping in application mappers.

## Services

- Service interfaces are contracts and need JavaDoc.
- Every handwritten production method needs JavaDoc, regardless of visibility — not only public/interface methods. Generated sources, Lombok-generated members, and `@Override` methods that inherit their contract are exempt.
- Replace magic strings and numbers with constants or enums.
- Business codes such as RBAC role/scope codes belong in enums with fields, not in static string bags.
- Outbound HTTP/SDK logic belongs in `backend/external-services` and should be configured through properties classes.

## Visibility and types (testability)

- No `private` methods in production beans/services. A `private` method cannot be spied or stubbed, which forces integration-style tests. Use package-private (default) visibility so same-package unit tests can mock/spy the method.
- When logic is non-trivial, algorithmic, or reused, extract a focused collaborator (`<Feature>Validator`, `<Feature>Policy`, `<Feature>Assembler`, `<Feature>Helper`) and unit-test it directly — do not grow a private-method pile or widen a method just to test it.
- Only `private static final` constants and `private final` injected fields stay private.
- No nested data types: declare records, DTOs, data-holding classes, and enums as top-level types in the module's `model`/`enums` package. Nested `@ConfigurationProperties` sub-groups are the only allowed nested types.

## Tests

- Match the repository's existing naming and comment style exactly.
- Prefer AssertJ fluent assertions.
- Prefer `ArgumentCaptor` over broad Mockito matchers.
- Keep tests local and explicit rather than DRY.
- Package-private production methods are unit-tested in the same package; `spy(new <Feature>ServiceImpl(...))` with `doReturn`/`doThrow` isolates a non-private self-call.
