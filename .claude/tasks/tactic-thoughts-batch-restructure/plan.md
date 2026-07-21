# Tactic-thoughts slide + Claude batch restructure — plan

Goal: add a new per-tactic "Thoughts on tactic performance" master slide, and
restructure the Claude passes in the slides-from-sheet flow so per-tactic
conclusions drive both that slide and the campaign-level narrative.

This doc is logic-only, for review before implementation.

---

## New slide

- New master slide "THOUGHTS ON TACTIC PERFORMANCE" (like breakdown masters).
- Holds 4 tokens: `{{thoughts on tactic n performance 1..4}}`.
- Duplicated per tactic **only when that tactic has > 2 breakdown toggles on**.
- Placed right after the tactic's **last** breakdown slide.
- Same duplicate/renumber mechanism as breakdown slides; needs a master id +
  object id in `application.yml`.

---

## Claude flow (slides-from-sheet)

Samap = existing brief digest `{{RFP info}}`, reused everywhere. Batch A
(strategic/proposal/audience) is generated as today and only touched in Step 5.

### Step 1 — sheet build (unchanged)
Brief/log compression + EOC sheet assembly. No change.

### Step 2 — per-tactic conclusions (data-driven, aligned to samap)
- `{{tactic n overview}}` — split out of old Batch C into its own call.
- 5 breakdown sections (publishers / creative / geo / audience / device) —
  as today, parallel, self-limiting.
- **Change: chunk size becomes 1 tactic per call (was 5).**
  - Configurable via `@ConfigurationProperties`, default = 1.
  - Add a semaphore capping concurrent Claude calls (rate-limit safety).
  - Verify prompt cache (`usage.cache_read`) lands before trusting the cost;
    fix CACHE_BREAKPOINT prefix first if it doesn't.

### Step 3 — per-tactic thoughts (NEW)
- Runs **only for tactics with > 2 breakdowns** (same gate as the slide).
- One Claude call per tactic, in parallel.
- Input = in-memory Step-2 outputs for that tactic only (overview + its
  breakdown bullets). No sheet re-read.
- Output = 4 `{{thoughts on tactic n performance N}}` (limits like
  `{{thoughts on the performance N}}`).

### Step 4 — campaign-level aggregation
- Fills `{{recommendation N}}`, `{{recommendation N text}}`,
  `{{thoughts on the performance N}}`, `{{Our results overview N}}`, and the
  frequency narrative (`f_opportunity/f_fact/f_storytelling`).
- Input = samap + per-tactic thoughts (Step 3) where available.
  For tactics without Step 3 (<= 2 breakdowns): use their `{{tactic n overview}}`
  + whatever breakdowns they do have.

### Step 5 — final storytelling + limit trim
- Per-tactic bundles in parallel + one campaign-level call.
- Enforces character limits AND harmonizes into one storyline.
- Also aligns Batch A (strategic) here.

---

## Dependency order

```
Batch A ─┐
Step 2 ──┼─> Step 3 ──> Step 4 ──> Step 5
         │   (>2 only)
```
Step 3 depends on Step 2; Step 4 on Step 3; Step 5 last. Within each step,
tactics run in parallel.

---

## Code touch points

- `ClaudeClient` port: split `batchResults` -> `batchTacticOverviews` (Step 2)
  + `batchCampaignResults` (Step 4); add `batchTacticThoughts` (Step 3);
  extend `batchAlignNarrative` to Step 5 (per-tactic + campaign variants).
- DTOs: split `ClaudeResults`; new DTO for the 4 thoughts; input DTO bundling a
  tactic's conclusions.
- `RealClaudeClient` + `ClaudeBatchPromptBuilder`: new prompt builders; configurable
  chunk size; concurrency semaphore.
- `ReportGenerationServiceImpl.runSlidesFromSheet`: reorder into 5 steps; collect
  per-tactic bundles; parallelize Steps 3 & 5 with `usageTracker.inScope`.
- New helper `TacticConclusionAssembler`: builds a tactic's bundle from
  `BreakdownValues` + tactic overviews.
- `RealSlidesProvider`: insert the new master when breakdown count > 2, after the
  last breakdown copy.
- Shared ">2 breakdowns" gate helper (used by slide insertion AND Step 3).
- `application.yml`: new master id + slide object id.
- Tests: prompt builders, assembler, >2 gate, reordered orchestration.

---

## Cost / time expectation

- Output tokens: ~unchanged.
- Input tokens: +20-35% (per-tactic bundles are small; no raw grids). Depends on
  prompt cache landing for the new chunk=1 breakdowns.
- Wall-clock: +2 sequential rounds (Step 3 after 2, Step 5 after 4), each parallel
  across tactics -> ~+20-40s on a big deck.
- Call count rises (chunk=1 + Steps 3/5 per tactic) — hence the semaphore.
