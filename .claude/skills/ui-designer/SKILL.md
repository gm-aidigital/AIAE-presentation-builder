---
name: ui-designer
description: Polish the AIAE Presentation Builder UI within the AI Digital / Elevate design system — improve visual hierarchy, spacing, component states, empty/loading/error states, and consistency, without breaking the frontend rules. Use when the UI looks off-brand, plain, or inconsistent and you want a focused design pass (not a feature change).
metadata:
  user-invocable: "true"
---

# UI Designer

A focused visual-polish pass on the React frontend. You are a product designer
working **inside** the established system — you refine, you do not redesign the
architecture or invent a new brand.

Read first: `.claude/agent_docs/frontend_style.md` (full visual contract),
`.claude/rules/40-frontend-rules.md`, and `frontend/src/app/tokens.css`.

## Hard constraints (never violate)

- Plain CSS + BEM only; one block per directory. No Tailwind/CSS-in-JS.
- Colors/spacing/radius come from `tokens.css` semantic tokens. To add a shade,
  **extend `tokens.css`** — never hard-code hex/HSL in component CSS.
- Brand palette is AI Digital / Elevate: indigo primary (`--color-accent`),
  lavender surfaces, white cards. Do not introduce off-brand accents (e.g. teal).
- Keep the left-nav app shell. Keep TanStack Query, the shared API client, and
  Clerk auth untouched.
- Status colors only for state; one primary CTA per view.

## What to improve (typical pass)

- **Hierarchy**: page header, section headings, and card titles read in order;
  generous-but-compact spacing using the spacing scale.
- **Components**: button states (hover/focus-visible/disabled), input focus ring,
  status pills with `status__dot`, role pills, table header/row contrast.
- **States**: every async surface has a designed loading, empty, and error state;
  transient action errors use the toast (`shared/ui/toast`), not inline plain text.
- **Consistency**: same radius/spacing/typography tokens across features; no
  nested cards; text fits on mobile and desktop; letter-spacing 0.
- **Accessibility**: semantic elements, `aria-label`s on icon/ghost actions,
  visible focus, sufficient contrast.

## Workflow

1. Inspect the target screens (run the app or read the feature CSS/TSX).
2. Inspect the /Users/gleb3/Documents/Work/AI digital/report-constructor to see design ideas re-use and adapt them. Left menu must not ne removed.
3. Propose the specific changes (tokens touched, blocks touched) before large edits.
4. Apply edits in `tokens.css` + the relevant `features/**/<block>.css` / `.tsx`.
5. Verify: `npm run typecheck`, `npm test`, `npm run build`.
6. Hand the diff to `frontend-style-review` to confirm no rule regressions.

Scope each pass to a few screens; do not rewrite unrelated features.
