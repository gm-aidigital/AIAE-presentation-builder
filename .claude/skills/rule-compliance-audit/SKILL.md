---
name: rule-compliance-audit
description: Audit the whole AIAE Presentation Builder repository against every rule in .claude/rules and .claude/agent_docs (backend + frontend), and produce a categorized findings report. Use for a periodic health check, before a handoff, or when asked whether the project satisfies its rules.
metadata:
  user-invocable: "true"
---

# Rule Compliance Audit

Whole-repository audit against the local rule set. Read-only — produces a report,
changes nothing. Load `.claude/rules/*.md` and `.claude/agent_docs/index.md`
first so the audit reflects the current rules.

Group findings by area; for each finding give `file:line — rule — fix`. End with
counts and `STATUS: COMPLIANT` or `STATUS: VIOLATIONS_FOUND`.

## Backend scanners

```bash
# No private methods on production beans/services
grep -rnE "(^|[[:space:]])private[A-Za-z0-9_<>,.\[\] ]* [A-Za-z0-9_]+[[:space:]]*\(" backend/*/src/main/java \
  | grep -vE "private (final|static final|volatile)" | grep -v /target/

# No nested data types (allow nested @ConfigurationProperties groups — verify enclosing class)
grep -rnE "^[[:space:]]+(public |protected )?(static )?(record|class|enum) [A-Z]" backend/*/src/main/java | grep -v /target/

# @ConfigurationProperties, not @Value
grep -rn "@Value" backend/*/src/main/java | grep -v /target/

# No static methods on beans (constants are fine)
grep -rnE "static [A-Za-z0-9_<>]+ [a-z][A-Za-z0-9_]*[[:space:]]*\(" backend/service backend/application/src/main/java | grep -v "static final" | grep -v /target/

# Repository access only via paired entity service
grep -rn "Repository " backend/application/src/main/java backend/service/src/main/java | grep -i inject -n; \
grep -rnl "Repository" backend/application/src/main/java/**/controller* 2>/dev/null
```

Then read-audit: JavaDoc on every handwritten method; controllers implement
generated `*Api` and stay thin; `1 entity = 1 repository = 1 service`; outbound
calls only in `backend/external-services`; business codes are enums; no edits to
`target/generated-sources`; structured JSON logging.

## Frontend scanners

```bash
# No raw HTTP / hardcoded backend URLs outside the shared client
grep -rnE "\b(fetch|axios|XMLHttpRequest)\b|http://localhost" frontend/src | grep -v "shared/api/client"

# No forbidden styling frameworks
grep -rnE "tailwind|styled-components|@emotion|\.module\.css" frontend/src frontend/package.json

# Hardcoded colors in component CSS (should use tokens)
grep -rnE "#[0-9a-fA-F]{3,6}|hsl\(|rgb\(" frontend/src/features frontend/src/shared | grep -v tokens.css

# Generated API types not hand-edited
git -C frontend diff --name-only -- src/shared/api/generated/schema.d.ts
```

Then read-audit: TanStack Query for server state; Clerk-only auth with Bearer
JWT through the shared client; BEM + one-block-per-directory; every async surface
covers loading/error/empty/success; the left-nav app shell is intact (this app
keeps its left sidebar — see `frontend_style.md`).

## Output

```
STATUS: COMPLIANT | VIOLATIONS_FOUND
Backend: <n> findings
Frontend: <n> findings
Findings:
- <area> — <file>:<line> — <rule> — <fix>
Accepted exceptions:
- <e.g. SecurityProperties.Cors — nested @ConfigurationProperties group>
```
