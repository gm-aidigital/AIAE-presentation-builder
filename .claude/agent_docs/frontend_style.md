# Frontend Style And BEM Rules

This is the full visual contract for the AIAE Presentation Builder frontend. It applies
the AI Digital / Elevate design system — **indigo primary (`hsl(239 100% 43%)`)
with lavender surfaces**, defined as semantic tokens in
`frontend/src/app/tokens.css`. The one deliberate deviation from the shared
template is that this app **uses a left-nav app shell** (explicitly chosen by the
product owner) instead of header-only navigation.

## Personality

Clean, utilitarian, and data-dense. This is an internal operations tool, not a
marketing page. The UI should feel quiet, precise, and built for repeated
workflows.

## Layout

- This app uses a **left-nav app shell**: a persistent left sidebar
  (`features/layout/sidebar`) plus a content area (`features/layout/app-shell`).
  This intentionally overrides the shared "no left menu" default — keep it.
- Sidebar holds brand, grouped nav, and the signed-in user + sign-out at the
  bottom. Admin-only groups (e.g. Management → User Management) render only for
  admins.
- Each page owns a compact `page-header` (title + subtitle + optional status),
  then dense content (tables, filters, grouped rows) — not oversized hero
  sections.
- One primary CTA per view. Secondary actions use `button--secondary`,
  `button--ghost`, menu, or icon buttons. Never two primary buttons in one row.

## Design tokens

All colors and scale values live in `frontend/src/app/tokens.css` as CSS custom
properties; components consume semantic tokens. Never hard-code hex/HSL in
feature or component CSS. Extend `tokens.css` before introducing a new value.

Current semantic tokens:

| Token | Use |
|---|---|
| `--color-bg` | page background (`hsl(240 60% 99%)`) |
| `--color-panel` | cards, sidebar, table/row surfaces (white) |
| `--color-text` | body text + headings |
| `--color-muted` | secondary copy, labels |
| `--color-border` | dividers, strokes, gridlines |
| `--color-accent` | primary CTA, links, active nav (indigo `hsl(239 100% 43%)`) |
| `--color-accent-hover` | primary button hover/active |
| `--color-accent-contrast` | text/icon on accent |
| `--color-accent-soft` | active nav background, soft notices |
| `--color-surface-muted` | table header / grouped-row surface (lavender) |
| `--color-success-bg` / `--color-success-text` | success/active state |
| `--color-neutral-bg` / `--color-neutral-text` | neutral status pill |
| `--color-danger` / `--color-danger-border` / `--color-danger-bg` | error / destructive |
| `--radius-card` (12px) / `--radius-control` (10px) / `--radius-pill` | card vs control vs pill radius |
| `--sidebar-width` | left-nav width (resizable, persisted to localStorage) |

Status colors communicate state only — never use them as decoration. Status pills
use a leading `status__dot` (a `currentColor` dot) so state reads at a glance.

## Typography

- Primary typeface: Inter, weights 400/500/600/700.
- Compact product type: page title `26–32px / 700`, section H2 `18px / 600`,
  subhead `16px / 500`, body `13–14px / 400`, table head `12–14px / 700`
  uppercase for column labels, pill `12px`.
- Body copy stays between `12px` and `16px`.
- Letter spacing stays `0`. No viewport-width font scaling.

## Shape and spacing

- Cards/panels use `--radius-card` (12px); controls/buttons/inputs use
  `--radius-control` (10px); pills use `--radius-pill` (999px). Do not mix
  unrelated radius scales (e.g. 4px and 20px).
- Common spacing increments: 6, 8, 12, 16, 22/24. Table/list rows use ~10–14px
  vertical padding and 12–14px horizontal padding.
- Parent layout blocks own spacing via `gap`/padding; blocks set no external
  margins (see CSS selector rules).

## Components

- Buttons: ~40px tall, `--radius-control`, `14px / 600–700`. `button` (primary
  accent), `button--secondary`, `button--ghost`.
- Inputs/selects: ~40px tall, `--radius-control`, semantic border/background.
- Status pills: rounded-full, state token only, concise label.
- Tables: muted header surface (`--color-surface-muted`), `--color-panel` rows,
  grid-based rows with explicit column tracks. Do not nest cards.
- Modals/dialogs: a single block (`*-dialog`) with overlay + panel; one primary
  confirm action.

## Styling baseline

- Use plain CSS files imported by React components.
- Use BEM class names for all project-authored styles.
- Do not introduce CSS Modules, Tailwind, styled-components, Emotion, or CSS-in-JS.
- Design tokens live in CSS custom properties and are consumed by components.
- Do not hardcode new colors in component CSS when a semantic token should exist.

## BEM syntax

Use one locked syntax:

```text
block
block__element
block--modifier
block__element--modifier
block--size-large
block__status--state-active
```

Rules:

- lowercase kebab-case inside names
- double underscore between block and element
- double hyphen before modifiers
- single hyphen only inside multi-word names
- modifiers compose with the base class: `button button--secondary`, not only `button--secondary`

Forbidden variants:

- `block_element`
- `block_modifier_value`
- `blockElement`
- ambiguous `block-element` where the separator does not identify element vs modifier

## CSS selector rules

- Prefer flat class selectors.
- Do not use IDs for styling.
- Do not use tag selectors in component CSS.
- Do not use descendant selectors such as `.card .card__title`.
- Blocks should not set external margins. Parent layout blocks own spacing through `gap` or padding.
- Avoid global resets inside feature/component CSS. Base reset and token layers belong in app/shared base CSS.

## File organization

For new or refactored UI, use one block per directory:

```text
frontend/src/features/<feature-name>/<feature-name>.tsx
frontend/src/features/<feature-name>/<feature-name>.css
frontend/src/features/<feature-name>/components/<sub-block>/<sub-block>.tsx
frontend/src/features/<feature-name>/components/<sub-block>/<sub-block>.css
```

The directory name, component CSS file, and root block class should match.

## Class name helper

A tiny local `cn` helper is acceptable for conditional class names. Do not add a dependency such as `clsx` only for this unless the project already uses it.

## UI quality rules

- Use the radius tokens (`--radius-card` 12px, `--radius-control` 10px); do not introduce ad-hoc radii.
- Use icons for icon-like actions when an icon library is already present.
- Do not create nested cards.
- Text must fit its container on mobile and desktop.
- Do not use viewport-width font scaling.
- Letter spacing must stay `0` unless a very specific brand treatment already exists.
- Avoid one-note palettes and decorative orb/blob backgrounds.
