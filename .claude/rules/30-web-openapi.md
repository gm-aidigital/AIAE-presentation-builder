---
description: Backend application-layer web and OpenAPI contract rules.
paths:
  - "backend/application/src/main/java/**/*.java"
---

# Web And OpenAPI Rules

- OpenAPI YAML is the contract source of truth.
- Controllers implement generated `*Api` interfaces.
- Do not replace generated-interface routing with handwritten `@RequestMapping` contracts.
- Keep controller methods focused on auth, service delegation, and mapping.
- Map service models to OpenAPI DTOs through application mappers.
- Centralize error mapping in `GlobalExceptionHandler` and its response helper.
