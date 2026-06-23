---
name: frontend-style-review
description: Review changed frontend code against the AIAE Presentation Builder design system and frontend rules (BEM + tokens, one block per directory, shared OpenAPI client only, TanStack Query, left-nav app shell kept, loading/error/empty/success states). Use before committing frontend changes or when asked to review UI for style/rule compliance.
metadata:
  user-invocable: "true"
---

# Frontend Style Review

Review frontend changes against `.claude/agent_docs/frontend_style.md` and
`.claude/rules/40-frontend-rules.md`. Read-only gate. Report
`file:line — rule — fix` then `STATUS: PASS | CHANGES_REQUESTED`.

Scope: the working diff by default; whole `frontend/src` on request.

## Scanners

```bash
# Raw HTTP / hardcoded URLs (must go through shared/api/client.ts)
grep -rnE "\b(fetch|axios|XMLHttpRequest)\b|http://localhost" frontend/src | grep -v "shared/api/client"

# Forbidden styling tech
grep -rnE "tailwind|styled-components|@emotion|\.module\.css" frontend/src frontend/package.json

# Hardcoded colors instead of tokens
grep -rnE "#[0-9a-fA-F]{3,6}|hsl\(|rgb\(" frontend/src/features frontend/src/shared | grep -v tokens.css

# Generated types not hand-edited
git -C frontend diff --name-only -- src/shared/api/generated/schema.d.ts
```

## Read-audit checklist

- **BEM**: `block`, `block__element`, `block--modifier`; lowercase kebab; flat
  class selectors; no IDs, tag selectors, or descendant selectors in component
  CSS; blocks set no external margins (parent owns spacing via gap/padding).
- **One block per directory**: `<block>.tsx` + `<block>.css` (+ `components/`),
  directory name = CSS file = root block class.
- **Tokens**: colors/spacing/radius come from `app/tokens.css`; status colors
  used only for state, never decoration; radius scales not mixed.
- **Layout**: the left-nav app shell (`features/layout/app-shell` + `sidebar`)
  is kept; pages use a compact `page-header`; one primary CTA per view.
- **Data**: TanStack Query for server state; every async surface renders
  loading / error / empty / success; Clerk auth via shared client with Bearer
  JWT; no secrets in frontend env.
- **Tests**: Vitest + Testing Library, `should …` naming, Given/When/Then,
  loading/error/empty/success and a critical-action success + error case.

## Output

```
STATUS: PASS | CHANGES_REQUESTED
Findings:
- <file>:<line> — <rule> — <fix>
```
