# Rule files — naming and loading convention

Files in `.claude/rules/` are auto-loaded by Claude Code. Each file's YAML
frontmatter decides *when* it loads, and the numeric prefix decides *ordering
and topic*. The two are independent — read both.

## Always-on vs path-scoped

- **Always-on (hard rules).** Frontmatter has only `description:` and **no
  `paths:`**. The body is in context for every task. There is one such file:
  `00-backend-hard-rules.md` — non-negotiable backend invariants.
- **Path-scoped (topic rules).** Frontmatter has a `paths:` glob list. The body
  loads only when a file matching those globs is touched. All other files are
  path-scoped.

So "hard rule" = no `paths`, always on. "Topic/scoped rule" = has `paths`. The
distinction lives in the frontmatter, not the filename — the `-hard-rules`
suffix is just a readable label for the always-on file.

## Numeric prefix = topic band

| Prefix | Topic | Scope |
|---|---|---|
| `00-` | Always-on backend invariants | always on |
| `10-39` | Backend topic rules | `backend/**` paths |
| `40-59` | Frontend topic rules | `frontend/**` paths |

Current files:

- `00-backend-hard-rules.md` — always-on backend invariants
- `10-architecture.md` — backend architecture / service boundaries
- `12-database.md` — persistence, JPA, Liquibase, caching
- `20-tests.md` — backend test style
- `30-web-openapi.md` — controllers and OpenAPI contract
- `40-frontend-rules.md` — frontend architecture, API, auth, styling
- `50-frontend-tests.md` — frontend test style

## Conventions when adding a rule file

- Pick the band that matches the topic and keep prefixes unique (no two files
  share a prefix).
- Add `paths:` unless the rule is a genuine always-on invariant; over-using
  always-on rules bloats every task's context.
- Keep each file short and imperative. Deep background belongs in
  `.claude/agent_docs/`, not here.
