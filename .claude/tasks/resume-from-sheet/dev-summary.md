# Dev summary — parts A + B (draft persistence and resume)

Implemented 2026-08-06. Part C is covered in the second half of this document.

## What a user sees

1. Finishing step 3 builds the workbook as before. The job now also stores the wizard state.
2. That run appears in **My reports** as a `DRAFT` row: "Sheet ready — not generated yet", with
   **View sheet ↗**, **Discard** and **Continue →**.
3. **Continue** opens `/reports/new?resume=<jobId>` and lands directly on step 4 (Review Sheet),
   with the sheet's figures read back and the earlier steps not navigable.
4. Generating the deck makes the draft row disappear on its own — the deck job records the same
   `sheet_url`, which is what marks the workbook as consumed.
5. **Discard** hides the row. Nothing is deleted: not the job, not the Google Sheet, not the run's
   contribution to the admin figures.

## Backend

| File | Change |
|---|---|
| `db/changelog/changes/0010-report-jobs-drafts.xml` | new `report_jobs.dismissed_at` |
| `ReportJobEntity` | `dismissedAt` field |
| `ReportResumeState` (new DTO) | the stored wizard state |
| `ReportResume` (new DTO) | that state + the workbook and source URLs |
| `ReportResumeStateHelper` (+impl, new) | distil from the payload, (de)serialise, never throws |
| `ReportDraftPolicy` (+impl, new) | `consumedSheetUrls` / `isDraft` / `isListed` |
| `ReportJobProgressHelper` (+impl) | `recordResumeState`, `dismissJob` (idempotent) |
| `ReportGenerationServiceImpl` | SHEET branch records the state before marking the job done |
| `ReportHistoryService` (+impl) | drafts in the list; `resumeForOwner`, `dismissForOwner` |
| `ReportSummary` / `ReportSummaryAssembler` | `draft` flag (admin view passes false) |
| `openapi.yaml` | `ReportSummaryV1.draft`, `ReportResumeV1`, two new paths |
| `ReportsApiMapper`, `ReportsController` | map and serve them |

Two decisions worth remembering:

- **`payload_json` was dead.** The column existed since `0002` and nothing ever wrote it. It stores
  the resume state now — no new column, no migration risk.
- **No new query.** "Has this workbook already produced a deck?" is answered over the owner's job
  list, which `historyForOwner` already loads in full. `consumedSheetUrls` is one pass over it.
  This matters because `[[postgres-only-sql-unverified-locally]]`: no Docker here, so a new native
  or jsonb query could not have been verified before deploy.

The stored blob is deliberately small — no source grids, no line-item mapping, no `bqSheetId`.
`ReportGenerationServiceImpl#narrativeOnly` already blanks all of those for the deck step, so
storing them would cost megabytes of jsonb per draft for values that are thrown away.

## Frontend

| File | Change |
|---|---|
| `useMyReports.ts` | `useDismissReport` mutation, invalidates the list |
| `useReportResume.ts` (new) | one-shot fetch, `retry: false`, `staleTime: Infinity` |
| `MyReportsPage.tsx` + css | draft badge, Continue / Discard, confirm that says nothing is deleted |
| `ReportConstructorPage.tsx` | `?resume=`, seeding effect, review rows without a mapping |
| `Stepper.tsx` | `minReached` |
| `StepReviewSheet.tsx` | `resumed` hides Back |
| `shared/api/types.ts` | `ReportResume`, `BreakdownSelection` aliases |

Three things a resumed session cannot do the normal way, and how each is handled:

- **No line-item mapping** → the review table is built from the sheet summary's own `tactic`
  names, falling back to the names the draft recorded while that read is in flight. A tactic
  renamed in the workbook shows its new name.
- **No breakdown toggles** → the draft's stored `breakdownSelections` are used verbatim for the
  generate payload and the "fill the Breakdowns tab" warning. Re-deriving them from an empty
  mapping would have inserted no breakdown slides at all.
- **No connected sources** → `mediaPlanUrl`/`elevateUrl` fall back to the ones the original session
  recorded, so finishing a report a day later does not erase its provenance in the admin view.

Navigation back to steps 1–3 is blocked (`minStep = 3`, Back hidden): those steps rebuild the sheet
from grids this session never loaded, and `buildSheet()` on empty inputs would produce a broken
workbook.

## Verification

- Backend: full `mvn test` — 411 service + 62 application tests. The only failures are the four
  known-environmental ones: `GitVersionServiceImplTest`, `MediaPlanTacticExtractorTest`
  ([[two-preexisting-test-failures]]), plus `AuthControllerTest` (6) and
  `LiquibaseChangelogSmokeTest` (1), which need Docker. **All four were confirmed to fail
  identically on a clean `HEAD` worktree**, so this change adds none.
- `ApplicationSmokeTest` passes, so the new beans wire in a real Spring context.
- `check-structure-strict.py backend` — OK.
- Frontend: `tsc --noEmit` clean for every touched file. Two pre-existing errors elsewhere
  (`AdminDashboardPage.tsx:246`, `PacingModal.tsx:263`) are unrelated and were left alone.
- **Frontend tests were written but not run** — vitest cannot be installed in this environment
  (see [[no-node-toolchain-locally]]). `MyReportsPage.test.tsx` and `ReportConstructorPage.test.tsx`
  need a run on Replit/CI.
- The Liquibase changelog was **not** executed against a real Postgres (no Docker). It is a plain
  `addColumn` of a nullable timestamp, matching `0005`'s shape.

---

# Dev summary — part C ("I already have a filled sheet")

Implemented on top of A + B the same day. It adds one endpoint and one card; everything after the
adoption is the resume path that already existed.

## What a user sees

On step 2, above the brief and the sheet connectors, a card: **"Already have a filled report
sheet?"** → paste the link → **Use this sheet**. The workbook is read and validated on the spot,
registered as a draft, and the wizard lands on step 4 (Review Sheet). No media plan, no Elevate
export, no matching, no pacing. Nothing is charged — the adoption makes no Claude call.

A rejected link says why ("no tactics found on its first tab", "could not read that Google Sheet"),
and the user stays on the form.

## Backend

| File | Change |
|---|---|
| `SheetTacticCountHelper` (+impl, new) | counts `{{tactic n}}` up to the first gap; `ReportGenerationServiceImpl#deriveTacticCount` now delegates to it instead of duplicating the loop |
| `BreakdownInferenceHelper` (+impl, new) | infers the per-tactic breakdown selections from the workbook |
| `SheetAdoptionService` (+impl, new) | validates, builds the resume state, writes the job row |
| `openapi.yaml` | `AdoptSheetRequestV1`, `POST /api/v1/reports/adopt-sheet` |
| `ReportsController` | one more thin delegation |

The adopted workbook is recorded as a **finished SHEET job** — `status = done`, `sheet_url` = the
user's own link, `payload_json` = the resume state. That is not a trick: from that moment it *is*
the same thing a normal run produces at that point, a reviewed workbook waiting to become a deck.
The draft policy, the history row and the resume endpoint all treat it identically, and generating
the deck retires it the same way.

### The inference rule, and why it needed no new sheet parsing

The plan proposed two signals (anchor present + block has data) and a new geometry helper in
`RealSheetDeckProvider`. On inspection one signal covers both cases, using readers that already
exist:

- a workbook this app built has its unselected sections **blanked, header anchor included**, so the
  existing section readers return empty tables for them;
- a workbook filled by hand from the template keeps every anchor, so "did the user type anything"
  is the only thing that separates a prepared section from an untouched one.

So the rule is just **a section is enabled when its table carries data**, answered by the five
`ReportSheetHelper.readXxxTables` calls — one per section for the whole workbook, not one per
tactic. No changes to `external-services` at all.

Known consequence, deliberately accepted: a section that was enabled but left blank is inferred as
disabled and its slide is dropped. For an adopted workbook the sheet is the only statement of
intent there is, and a blank section would have produced a blank slide.

### Two things the sheet cannot supply

**No date window.** `SheetCampaignReader` only reads `{{flight_dates}}` as display text — it never
populates `flightTs` — and that cell is free text this app did not write. The adopted state stores
no `dateFilter` rather than parsing it. Safe: the slides-from-sheet step runs the narrative pass
over blanked grids, so the filter gates nothing, and the sheet's own value wins for the rendered
`{{flight_dates}}`.

**Possibly no brief.** `ReportGenerationService#start` rejects a blank brief, and an adopted
workbook may have an empty `{{RFP info}}` cell. Rather than relax that check (the brief is what the
entire narrative is written from) or invent a placeholder, the review step asks for it: when the
draft arrives with no brief, a required textarea appears and Confirm is blocked until it is filled.
Whether to show it is decided once on mount, so the field cannot vanish under the user mid-typing.

## Frontend

| File | Change |
|---|---|
| `useAdoptSheet.ts` (new) | the mutation; surfaces the server's own rejection message |
| `StepDataInputs.tsx` | the `AdoptSheetCard`, collapsed by default, above the form |
| `StepReviewSheet.tsx` | the brief field for a workbook that carried none |
| `ReportConstructorPage.tsx` | `adoptSheet()` — navigates to `?resume=<jobId>` |
| `report-constructor.css` | `.rc-adopt*`, `.rc-banner__field` |

The adopt response already contains the whole draft, but the page **navigates to `?resume=<jobId>`
instead of seeding from it**. One extra GET buys the thing the feature is for: an adopted sheet
survives a reload from the moment it is adopted.

## Verification (part C)

- Backend: 425 service tests, only the two known-environmental failures
  ([[two-preexisting-test-failures]]); 62 application tests with the seven that need Docker.
  `ApplicationSmokeTest` passes, so the three new beans wire in a real context.
- New tests: `SheetTacticCountHelperImplTest`, `BreakdownInferenceHelperImplTest`,
  `SheetAdoptionServiceImplTest` (rejection paths assert no job row is created).
- `check-structure-strict.py backend` — OK.
- Frontend: OpenAPI types regenerated, `tsc --noEmit` clean for every touched file.
- **Frontend tests still not run** — vitest cannot be installed here
  ([[no-node-toolchain-locally]]). The adopt cases were added to
  `ReportConstructorPage.test.tsx` and need a run on Replit/CI.
- Not exercised against a real workbook: the inference reads a real "Breakdowns" tab through
  mocked readers in tests. The first adoption of a genuine hand-filled workbook is worth watching —
  the log line `[breakdowns] inferred selections for N tactic(s)` prints exactly what it decided.
