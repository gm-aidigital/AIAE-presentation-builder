---
name: backend-rule-review
description: Review changed backend Java code against the AIAE Presentation Builder backend rules (no private methods, no nested data types, JavaDoc on every method, 1=1=1 boundaries, @ConfigurationProperties, thin controllers, enums over magic strings). Use before committing backend changes or when asked to review backend code for rule compliance.
metadata:
  user-invocable: "true"
---

# Backend Rule Review

Review backend changes against `.claude/rules/00-backend-hard-rules.md`,
`.claude/agent_docs/code_conventions.md`, and
`.claude/agent_docs/service_architecture.md`. Pure gate — does not change code.
Report each finding as `file:line — rule — what to do`, then a final
`STATUS: PASS` or `STATUS: CHANGES_REQUESTED`.

Scope: the working diff by default (`git diff --name-only` + staged). Review the
whole backend when the user asks for a full pass.

## Checks

Run these from the repo root; treat hits as findings to inspect (not all hits
are violations — judge each).

### Visibility and testability (no private methods)

```bash
grep -rnE "(^|[[:space:]])private[A-Za-z0-9_<>,.\[\] ]* [A-Za-z0-9_]+[[:space:]]*\(" backend/*/src/main/java \
  | grep -vE "private (final|static final|volatile)" | grep -v /target/
```

Any hit is a violation: production beans/services have **no `private` methods**.
Make it package-private, or extract a `Validator`/`Policy`/`Assembler`/`Helper`.
Only `private static final` constants and `private final` fields stay private.

### No nested data types

```bash
grep -rnE "^[[:space:]]+(public |protected )?(static )?(record|class|enum) [A-Z]" backend/*/src/main/java | grep -v /target/
```

A nested record/DTO/data class/enum is a violation — extract to a top-level type
in the module's `model`/`enums` package. Allowed exception: nested
`@ConfigurationProperties` sub-groups (verify the enclosing class is a
properties class).

### JavaDoc on every handwritten method

Every method that is not generated, not Lombok, and not an inheriting
`@Override` needs a JavaDoc block immediately above it. Scan changed production
files and flag methods whose preceding line is not `*/` or `@Override`.

### Architecture and configuration

```bash
grep -rn "@Value" backend/*/src/main/java | grep -v /target/                 # use @ConfigurationProperties instead
grep -rn "Repository" backend/application/src/main/java/**/controller* backend/service/src/main/java/**/rbac backend/service/src/main/java/**/agency 2>/dev/null | grep -v /target/  # controllers/orchestrators must not inject repositories
grep -rnE "static [A-Za-z0-9_<>]+ [a-z][A-Za-z0-9_]*[[:space:]]*\(" backend/service backend/application/src/main/java | grep -v "static final" | grep -v /target/  # no static methods on beans
```

Also verify by reading the diff:

- Controllers implement generated `*Api` interfaces and stay thin (auth, service
  call, mapping only).
- `1 entity = 1 repository = 1 service`; only the paired entity service injects
  the repository.
- Outbound HTTP/SDK only under `backend/external-services`.
- Business codes (roles/scopes/statuses) are enums with fields, not inline
  string/number literals.
- No edits under `backend/application/target/generated-sources`.
- New logging is structured JSON.

## Output

```
STATUS: PASS | CHANGES_REQUESTED
Findings:
- <file>:<line> — <rule> — <fix>
Notes: <anything judged acceptable, e.g. nested @ConfigurationProperties group>
```
