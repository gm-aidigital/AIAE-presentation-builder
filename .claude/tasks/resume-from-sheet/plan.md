# Resume from sheet — drafts in "My reports" + "I already have a sheet"

> **Status (2026-08-06): parts A, B and C are implemented.** Drafts are persisted, listed,
> resumable and dismissible; a user-supplied workbook can be adopted from step 2 and goes through
> the same door. See `dev-summary.md` for what shipped and where it departs from this plan.

Two user-facing problems, one mechanism:

1. **Draft loss.** The EOM/EOC template sheet is filled by hand over a day or more. All wizard
   state lives in React memory (`WizardContext` says so explicitly: "no localStorage"), so closing
   the tab means walking steps 1–3 again just to get back to the same sheet.
2. **Bring your own sheet.** A user who already has a filled workbook must still walk the whole
   media-plan / Elevate / matching / pacing flow to reach the deck.

Both are the same thing: **enter the wizard at step 4 (Review Sheet) with only a sheet URL and a
handful of scalars.** Build (1) first; (2) is a second entry point on the same mechanism.

---

## Finding: step 5 barely depends on steps 2–3

Verified in code, not assumed.

- The frontend already blanks the heavy inputs when it launches the deck job —
  `sheetRows: [], adjRows: [], audienceRows: [], estimatesRows: [], geoRows: [],
  lineItemMapping: undefined, bqSheetId: undefined`
  ([`ReportConstructorPage.tsx:549`](../../../frontend/src/features/report-constructor/ui/ReportConstructorPage.tsx)).
- The backend blanks even more: `narrativeOnly()`
  ([`ReportGenerationServiceImpl.java:924`](../../../backend/service/src/main/java/com/aidigital/reportconstructor/service/reports/services/impl/ReportGenerationServiceImpl.java))
  nulls `marketVolume` and every grid before the narrative placeholder pass.

Everything `runSlidesFromSheet` actually consumes from the payload:

| field | source when steps 2–3 never ran |
|---|---|
| `sheetUrl` | the user's link |
| `reportType` | step 1 (EOC/EOM) — picks the Claude prompt flavour and which slides get deleted |
| `brief` | sheet `{{RFP info}}` already wins over the payload; payload value is the fallback |
| `changeLog` | sheet `{{change log}}` already wins; payload value is the fallback |
| `dateFilter` | narrative-only; the sheet overlay wins for `{{flight_dates}}` |
| `estimateDaypartGender` | a checkbox |
| `breakdownSelections` | **the only real gap** — see below |

Client name, tactic names/count, plan vs fact, market volume, audience copy, frequencies and the
pacing series for the charts are all read back out of the workbook
(`SheetPlaceholderReader`, `SheetCampaignReader`, `SheetChartDataReader`).

**Conclusion: a filled workbook is a sufficient input for the deck.**

### The breakdown gap

`breakdownSelections` does two things in the deck flow and one in the sheet flow:

- sheet flow (step 3 → SHEET job): `clearUnselectedBreakdowns` wipes the sections a tactic did not
  enable on the workbook's "Breakdowns" tab;
- deck flow: decides which breakdown slides get duplicated (`addBreakdownSlides`,
  `buildBreakdownCharts`) and which tactics qualify for Step-3 "thoughts" (`BreakdownThoughtsGate`,
  > 2 sections).

For a resumed draft we know the selections (they were sent with the SHEET job — just persist them).
For a foreign workbook we do not. **Decision: infer them from the workbook.**

---

## Part A — persist the draft (backend)

### A1. Liquibase `0010-report-jobs-drafts.xml`

New changelog under `backend/db/src/main/resources/db/changelog/changes/`, registered above the
marker comment in `db.changelog-master.xml`.

- `report_jobs.dismissed_at TIMESTAMPTZ NULL` — the user's "close and forget".
- Index supporting the draft lookup: `(owner_user_id, target, status)` — or extend whatever index
  `listJobsByOwner` already uses. Check `0002-report-jobs.xml` before adding a duplicate.

No new column for the resume blob: `report_jobs.payload_json` (jsonb) **already exists and is never
written** — grep confirms only `ReportJobEntityTest` touches `setPayloadJson`. Use it.

### A2. Write the resume blob on the SHEET job

`ReportGenerationServiceImpl` line ~296 (the `GenerationTarget.SHEET` branch) already calls
`jobProgress.recordArtifact(jobId, fileName, sheetUrl)` on success. Add, right there, a
`jobProgress.recordResumePayload(jobId, blob)`.

Blob shape (new top-level record in `service/reports/dto`, e.g. `ReportResumeState` — **not** a
nested type, per the hard rules):

```
reportType, brief, changeLog, marketVolume, dateFilter,
estimateDaypartGender, breakdownSelections, tacticNames (List<String>)
```

Raw grids stay out — they are unused downstream and would bloat jsonb badly (the geo bundle is the
whole workbook). Serialize with the Jackson mapper already used for `warnings_json`; follow how
`ReportGenerationWarningsHelper` does it.

`tacticNames` is a nicety: the Review Sheet table can also get names from `SheetSummaryRowV1.tactic`,
which the sheet-summary endpoint already returns. Keep it anyway so the table has labels before the
summary read lands.

### A3. Surface drafts in "My reports"

`ReportHistoryServiceImpl.isDeliverable()` currently drops every SHEET job. Replace with a small
collaborator (a `ReportDraftPolicy` — keep `ReportHistoryServiceImpl` from growing a private-method
cluster, per `10-architecture.md`):

A SHEET job is a **draft row** when all hold:
- `status = done`
- `dismissed_at IS NULL`
- no `SLIDES_FROM_SHEET` job of the same owner carries the same `sheet_url`
  (that's the "the deck was already built from it" test — `sheet_url` is recorded on the deck job at
  `ReportGenerationServiceImpl:613` via `recordArtifact(jobId, fileName, payload.sheetUrl())`)

Everything else stays as today. Add `status: "draft"` (or a `draft: true` flag) to `ReportSummary`
+ `ReportSummaryV1` so the frontend can style it without re-deriving the rule. New enum for the
lifecycle code if one does not exist — no magic strings.

Loading the deck jobs for the "already used" test needs a repository query keyed on
`(owner_user_id, target, sheet_url)`; it belongs in `ReportJobRepository` and is exposed through the
paired entity service (`1 entity = 1 repository = 1 service`).

### A4. Endpoints (OpenAPI first, `openapi.yaml`)

- `GET /api/v1/reports/{jobId}/resume` → `ReportResumeV1` — the blob from A2 plus `sheetUrl`.
  Owner-scoped: another user's job is a 404, same as `getReportJob`.
- `POST /api/v1/reports/{jobId}/dismiss` → 204. Sets `dismissed_at`. Idempotent.

Both go on the existing `Reports` tag; the controller (`ReportsController`) implements the generated
interface and stays thin.

**Do not hand-edit generated sources.** After editing the spec, regenerate:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21) && mvn -q -pl backend/application -am generate-sources
```

Frontend types regenerate from the same spec (see `frontend/package.json`).

---

## Part B — resume in the constructor (frontend)

### B1. "My reports" row

`MyReportsPage` gets a draft variant:
- badge `DRAFT` (new `mr-badge--draft` modifier, semantic tokens only — no new colour literals);
- primary action **Continue →** → `navigate("/reports/new?resume=" + r.jobId)`;
- secondary **Discard** → `POST .../dismiss` + `invalidateQueries(["reports","mine"])`, with a
  confirm (the sheet itself is not deleted — say so in the confirm text);
- "View sheet ↗" stays; "Open report →" is hidden for drafts (there is no deck).

New hook `useDismissReport` next to `useMyReports`; TanStack Query mutation, shared `apiClient` only.

### B2. Constructor entry at step 4

In `ReportConstructorPage`:
- read `?resume=<jobId>` (`useSearchParams`);
- fetch the resume payload, seed the wizard (`setBrief`, `setChangeLog`, `setReportType`,
  `setMarketVolume`, `setDateWindow`, `setEstimateDaypartGender`), set `sheetUrl`, keep
  `breakdownSelections` from the blob, then `setStep(3)`;
- while it loads, show a loading state — do not flash step 0;
- on 404/403, toast and fall back to a clean step 0.

**Navigation lock.** Steps 1–3 cannot be re-entered on a resumed session: the media-plan/Elevate
grids are gone, so `confirmInputs()` and `buildSheet()` would run on empty inputs. Add a
`resumed` flag that pins the `Stepper`'s navigable range to 3–4 (extend `Stepper` with a
`minReached`, or pass a `navigableFrom`). `StepReviewSheet`'s "Back" must be hidden in that mode.

**Review rows without a mapping.** `reviewRows` is built from `w.activeMapping`
([`ReportConstructorPage.tsx:405`](../../../frontend/src/features/report-constructor/ui/ReportConstructorPage.tsx)),
which a resumed session does not have. Change it to build from `summaryRows` when the mapping is
empty — `SheetSummaryRowV1` already carries `tactic`. `lineId` is display-only; render `—`.

**Generate payload.** `basePayload()` reads `w.mediaPlan?.sheetId` etc.; with nothing connected it
already degrades to empty/undefined, which is exactly what the deck job wants. Confirm
`mediaPlanUrl`/`elevateUrl` come from the resume blob if we want the admin history to keep pointing
at the sources (they are persisted on the SHEET job's own row — carry them into the blob or read
them from the summary).

### B3. Breakdown selections on resume

Straight from the blob. No inference needed for path (1).

---

## Part C — "I already have a filled sheet"

### C1. Entry point

On step 2 (`StepDataInputs`), a distinct card above the two connect cards — visually outside the
normal flow, e.g. a bordered "Already have a filled workbook?" panel with a URL field and a
**Use this sheet →** button. Not a modal: it must be visible without a click.

### C2. Server-side adoption

New endpoint `POST /api/v1/reports/adopt-sheet` (body: `sheetUrl`, `reportType`) →
`ReportResumeV1`, the same shape B2 already consumes. It:

1. reads the grid (`ReportSheetHelper.readSheetGrid`) with the user's Google token;
2. runs `SheetPlaceholderReader` + `deriveTacticCount`; **rejects** with a clear message when the
   tactic count is 0 or the client-name/flight-dates anchors are missing — a wrong link must fail
   here, not three Claude batches later;
3. pulls `brief` from `{{RFP info}}`, `changeLog` from `{{change log}}`, flight dates from the
   sheet;
4. **infers `breakdownSelections`** (C3);
5. creates a `report_jobs` row with `target = SHEET`, `status = done`, `sheet_url` = the link and
   the blob in `payload_json`, so an adopted sheet is a draft like any other and survives a reload
   the same way. Zero Claude cost — no batch runs.

The frontend then navigates to `/reports/new?resume=<newJobId>` and B2 handles the rest. One code
path, two doors.

`brief` may come back empty (a workbook whose `{{RFP info}}` was never filled). `start()` rejects a
blank brief, so B2 must show the brief field as editable-and-required in that case rather than
failing at Generate.

### C3. Inferring the breakdown selections (decided)

A new `BreakdownInferenceHelper` in `service/reports/helpers` (interface + `impl`, JavaDoc on every
method, no private methods):

```
Map<Integer, Set<BreakdownType>> infer(String sheetUrl, int tacticCount, String userGoogleToken)
```

Two signals, because the two kinds of workbook differ:

1. **Anchor presence.** `clearUnselectedBreakdowns` → `RealSheetDeckProvider.breakdownClearRequests`
   (~line 1616) blanks the *whole block including its `"Top Publishers 3"` header anchor*. So on a
   workbook that already went through our step 3, a missing anchor is a definitive "disabled".
2. **Block has data.** On a workbook the user built by hand from the template, every anchor is still
   there and only the data is missing. So an anchor that survives still has to have at least one
   non-blank cell in its block.

Rule: **enabled = anchor present AND its block carries data.** Reuse `findBreakdownAnchors` /
`breakdownBlockHeight` / `nextAnchorColOnRow` — the geometry is already solved there; extract it into
a shared collaborator on the provider rather than reimplementing the offsets. One read of the
"Breakdowns" tab serves every tactic and every section.

Reduce to `List<BreakdownSelection>` in the same order the payload expects (dense `1..N`).

Show the result in the UI: on the resumed Review Sheet step, a read-only line per tactic —
"Breakdowns detected: Top Publishers, Geo" — so a mis-detection is visible before the deck is paid
for. (Making them editable is a later increment; the user asked for link-only.)

---

## Test plan

Backend (`.claude/rules/20-tests.md` style):
- `ReportDraftPolicyTest` — draft vs deck vs dismissed vs "sheet already turned into a deck".
- Resume-blob round trip: serialize → persist → read back, with null `dateFilter` and null
  `breakdownSelections`.
- `BreakdownInferenceHelperTest` — cleared block (no anchor) ⇒ disabled; anchor present but block
  empty ⇒ disabled; anchor + one filled cell ⇒ enabled; no anchors at all ⇒ nothing enabled; tactic
  numbers above `tacticCount` ignored.
- MVC tests for the three new endpoints incl. the 404-on-other-owner case.
- `ReportGenerationServiceImplTest` — the SHEET branch records the blob.

Frontend (`.claude/rules/50-frontend-tests.md`):
- `MyReportsPage` — draft row renders Continue/Discard, deck row unchanged, Discard calls the
  mutation and invalidates.
- `ReportConstructorPage` with `?resume=` — lands on step 4, back-navigation to 1–3 blocked,
  review rows render from `summaryRows` with no mapping.
- `StepDataInputs` — the "already have a sheet" card posts and navigates.

Note the two known-environmental failures on `main` (`GitVersionServiceImplTest`,
`MediaPlanTacticExtractorTest`): a run with exactly those two is green.

Before pushing, the deploy gate:

```bash
python3 scripts/lib/check-structure-strict.py backend
```

---

## Build order

1. A1 + A2 + A3 — drafts exist and are listed (no UI yet; verify via the API).
2. A4 + B1 + B2 — the full resume loop. **Ship this; it solves problem 1 on its own.**
3. C3 — inference, unit-tested against a real exported workbook.
4. C1 + C2 — the "already have a sheet" door.

## Open items

- Draft retention: no expiry proposed. If the list gets noisy, sort drafts first and auto-dismiss
  after N days — needs a product call, not a technical one.
- Editing breakdown toggles for an adopted sheet is deferred (C3 renders them read-only).
- `dateFilter` on an adopted sheet is parsed from `{{flight_dates}}`; if that cell's format turns out
  to vary, fall back to leaving it `ALL` — the sheet overlay wins for the rendered value anyway.
