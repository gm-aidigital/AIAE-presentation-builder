# Task Workspace

This directory stores Claude task artifacts for this repository.

Recommended per-task structure:

- `.claude/tasks/<task>/plan.md`
- `.claude/tasks/<task>/dev-summary.md`
- `.claude/tasks/<task>/review-report.md`
- `.claude/tasks/<task>/test-report.md`

For this repository, do not require a separate `integration-test-report.md` by default.
The backend currently has unit tests, MVC tests, and repository query tests, but no dedicated `*IT` integration-test layer.
The frontend currently has Vitest/Testing Library component tests, API/client tests, and config/helper tests.
