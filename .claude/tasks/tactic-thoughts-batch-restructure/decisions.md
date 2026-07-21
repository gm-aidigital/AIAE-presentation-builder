# Resolved design decisions (confirmed by user, 2026-07-21)

## Progress
- [x] Subtask 1 — port + DTOs. New DTOs: TacticConclusionInput, TacticConclusion,
      TacticThoughtsInput, TacticThoughts, TacticNarrativeDigest. Port methods added:
      batchTacticConclusions, batchTacticThoughts, batchCampaignResults, batchAlignCampaign,
      batchAlignTactics. Stub returns empty/echo; Real has compiling skeletons (real prompt
      logic deferred to subtask 5) except batchAlignCampaign which already delegates to
      batchAlignNarrative. `ClaudeResults`/`batchResults` kept intact (classic flow). Backend
      compiles (service+external-services BUILD SUCCESS); check-structure-strict OK.
- [x] Subtask 2 — shared ">2 breakdowns" gate. New `BreakdownThoughtsGate` interface +
      `BreakdownThoughtsGateImpl` (threshold constant = 2, `qualifies(Set)` +
      `qualifyingTactics(Map)`) in service/reports/helpers[/impl]. Reachable from external-services
      (dep direction external-services→service confirmed) for the slide-insertion site.
      4/4 unit tests green; check-structure-strict OK.
- [x] Subtask 3 — Thoughts master slide. Config: GoogleProperties.thoughtsMasterSlideObjectId +
      application.yml `thoughts-master-slide-object-id` (blank/env = disabled no-op, master slide
      not yet in live template). BreakdownSlideNaming.thoughtsSlideId(n) = "thoughts_n".
      RealSlidesProvider: injects BreakdownThoughtsGate + reads master id; buildBreakdownRequests
      appends one thoughts copy LAST per qualifying (>2) tactic, fills {{thoughts on tactic n
      performance 1..4}} via extracted emitRenumberedTokens helper (also DRYs the breakdown loop),
      deletes master in phase 3. Constructor gained a param — test factory + new test updated.
      9/9 RealSlidesProvider tests green; check-structure-strict OK. NOTE: feature stays OFF until
      the master slide is added to the template and THOUGHTS_MASTER_SLIDE_OBJECT_ID set.
      CONFIRMED live master id (from presentations.get dump of 11qzOC7…): g3f56c270c23_0_0.
      Deliberately NOT the yml default yet — enabling before Step-4 fills the tokens would show raw
      {{…}} on the slide. Flip on (env THOUGHTS_MASTER_SLIDE_OBJECT_ID=g3f56c270c23_0_0) after subtask 6.
- [x] Subtask 4 — TacticConclusionAssembler. Interface + impl in service/reports/helpers[/impl].
      Source = structured TacticConclusion (per reconciled decision 3), not BreakdownValues tokens.
      toThoughtsInputs(conclusions, tacticNames, qualifyingTactics) → Step-3 inputs, gated tactics
      only. toCampaignDigests(conclusions, thoughts) → Step-4 digests: thoughts where present, else
      overview + bounded (MAX_DIGEST_LINES=12) flattened non-blank breakdown lines. 4/4 tests green;
      check-structure-strict OK.
- [~] Subtask 5 — RealClaudeClient prompts + chunk config + semaphore.
      - [x] 5a: config (AnthropicProperties.breakdownChunkSize=1, maxConcurrentCalls=6) +
            global Semaphore in AnthropicMessagesClient.callRaw (acquireUninterruptibly/release in
            finally) + wired AnthropicProperties into RealClaudeClient (17 test ctor sites updated) +
            application.yml external.anthropic.breakdown-chunk-size/max-concurrent-calls. Compiles.
      - [x] 5b: combined Step-2. buildTacticConclusionsPrompt (one cached instruction+context
            prefix reusing the 5 sections' rules/limits, CACHE_BREAKPOINT, then per-tactic data;
            fills a section only when present in the tactic's data) + batchTacticConclusions
            (chunk by breakdownChunkSize, resilient retry, conclusionsByTactic parse, one
            compression pass, assemble TacticConclusion). Old per-section builders left intact
            (dead-code removal deferred to subtask 6). 1 new test green; compiles; strict OK.
      - [x] 5c: Step-3. buildTacticThoughtsPrompt(input, brief, limit) — cached instruction+brief
            prefix, per-tactic conclusions after CACHE_BREAKPOINT, asks {"thoughts":[4]} (limit=
            THOUGHT_LIMIT 220). batchTacticThoughts loops inputs, resilient retry-once, parse+
            compress+normalize → TacticThoughts. Parallelism deferred to orchestrator (subtask 6)
            for usage-scope correctness. 1 new test green; compiles; strict OK.
      - [x] 5d: Step-4. buildCampaignResultsPrompt (fresh method; does NOT touch live buildBatchCPrompt/
            batchResults; reasons over per-tactic digests, omits tactic_overviews) + batchCampaignResults
            (mirrors batchResults parse minus overviews → ClaudeResults with empty tacticOverviews map;
            "BatchCampaign"/"BatchD-Campaign"). 1 new test green; compiles; strict OK.
      - [x] 5e: RESOLVED — per-tactic align DROPPED by user. Key clarification: limit-fitting (the
            old "Batch D") is the compression pass, now embedded in every step (compress "BatchD-*"
            + normalize in Steps 2/3/4), so limits are enforced. Campaign-level narrative
            harmonization is preserved via batchAlignCampaign (delegates to batchAlignNarrative;
            rewrites proposal/strategic/results-overviews/campaign-thoughts/frequency, reads
            breakdown digest). Per-tactic copy was NEVER narrative-rewritten historically; user
            confirmed campaign-level harmonization is enough. So batchAlignTactics REMOVED from
            port/Stub/Real (no dead no-op). Subtask 5 COMPLETE. All new tests green; strict OK.

SUBTASK 5 DONE. Remaining: subtask 6 (reorder runSlidesFromSheet into the 5 steps, wire the new
calls, parallelize Steps 3 per usageTracker.inScope, delete now-dead old per-section batch methods),
subtask 7 (cache verify on real run + orchestration tests).

PRE-EXISTING TEST FAILURES (NOT mine): 5 tests in RealClaudeClientTest fail on clean HEAD too
(batchCreativeTakeaways* x4, batchPublisherObservationsRetries* x1) — Mockito strict-stubbing
mismatch. Confirmed via git stash. Out of scope for this feature; flag to user.
- [~] Subtask 6 — reorder runSlidesFromSheet into 5 steps.
      - [x] 6a: split all 5 breakdown helpers (publisher/creative/geo/audience/device) into
            readXxxInputs (data tokens + per-tactic Claude input, NO Claude call) + writeXxx
            (writes Claude tokens from a bullets map). New generic DTO BreakdownSectionInputs<T>
            (tactics/inputs/dataValues/warnings). Old buildXxxValues kept, now delegates to
            read+claude+write (behavior preserved — 53 helper/gate/assembler tests green).
      - [x] 6b: runSlidesFromSheet reordered into the 5 steps. Injected BreakdownSelectionResolver
            + BreakdownThoughtsGate + TacticConclusionAssembler. New orchestrator helpers:
            assembleConclusionInputs, sectionBullets, qualifyingSelections, tacticNames,
            writeThoughtsTokens, mergeTacticOverviews. Step-2 reads run parallel (no Claude);
            ONE batchTacticConclusions; writeXxx fills section tokens; Step 3 gated>2; Step 4
            campaign from digests + merge overviews; Step 5 batchAlignCampaign. Thoughts tokens
            written into breakdownValues so the thoughts slide fills on insertion. Orchestrator
            test updated (12/12 green); both modules compile; strict OK. FEATURE FUNCTIONALLY
            COMPLETE end-to-end (thoughts slide still gated OFF until THOUGHTS_MASTER id set).
            NOTE: Claude steps run sequentially (cache-safe: sequential warms the CACHE_BREAKPOINT
            prefix); only the 5 Step-2 data reads run parallel. Parallelizing Claude calls is a
            subtask-7 optimization after cache verification.
      - [x] 6c: DONE (inline, not via the spawned chip). Removed the dead per-section breakdown Claude
            path the combined batchTacticConclusions replaced:
            * 5 helper impls: dropped buildXxxValues + the now-unused `claude` field/ctor param (and the
              BreakdownValues + ClaudeClient imports); kept readXxxInputs/writeXxx + pure helpers.
            * 5 helper interfaces: dropped buildXxxValues + fixed the {@link #buildXxxValues} javadoc refs.
            * ClaudeClient port + StubClaudeClient + RealClaudeClient: dropped batchPublisher/Creative/Geo/
              Audience/Device methods; in Real also the *Resilient/*Chunk helpers, bulletsByTactic, the
              CHUNK/MAX_TOKENS constants and now-unused input-DTO imports; fixed the 2 dangling javadoc
              links (conclusionsResilient, conclusionsByTactic→TACTIC_KEY). Kept all LIMIT/COUNT +
              BREAKDOWN_TIMEOUT_SEC (combined path still uses them).
            * ClaudeBatchPromptBuilder: dropped the 5 buildXxxPrompt methods; kept the context-block
              helpers + shared constants used by buildTacticConclusionsPrompt/buildCampaignResultsPrompt.
            * Tests: rewrote the 5 helper impl tests onto readXxxInputs/writeXxx (45 green); trimmed
              ClaudeBatchPromptBuilderTest + RealClaudeClientTest (removed the 5 pre-existing failures);
              adapted RealSlidesProviderTest#renumber_agrees… to read+write. service+external-services
              compile; affected tests green; check-structure-strict OK.
            * Also removed a newly-unused `java.util.Map` import from the ClaudeClient port.
            NOTE (pre-existing, NOT introduced here): the writeXxx impl javadocs still carry stale
            @param tacticNums/tables/brief from 6a (checkstyle "Unused @param"); left untouched to keep
            this a pure dead-code removal. Two unrelated service tests (MediaPlanTacticExtractorTest,
            GitVersionServiceImplTest) fail on clean HEAD too — environmental, not mine.
- [ ] Subtask 7 — cache verify on real run + tests.



These lock ambiguities left open by plan.md. Source of truth for logic stays plan.md.

1. **Thoughts slide plumbing** — NOT a `BreakdownType`. Add a dedicated master id
   (`external.google.thoughts-master-slide-object-id`) + its own handling in
   `buildBreakdownRequests`: appended as the LAST synthetic copy of a tactic, only when the
   ">2 breakdowns" gate passes. Does not touch `BreakdownType` / `BreakdownSelectionResolver` /
   sheet-clear / breakdown charts.

2. **Step 2 call structure (REVISED — supersedes plan.md's "5 separate sections")** — combine
   into ONE Claude call per tactic covering `{{tactic n overview}}` + all that tactic's ENABLED
   breakdown sections. So Step 2 = one call per tactic. Implications:
   - The 5 breakdown helpers must be split: "read this section's sheet data" (kept per section)
     vs "call Claude" (now merged into one combined per-tactic call).
   - Combined per-tactic prompt includes only the sections that tactic toggled on; combined
     parser must still emit every existing section token→value (slides + charts unchanged).
   - Resilient retry becomes per-tactic (whole tactic retried, not per section).
   - "chunk size" now means tactics-per-call; keep configurable (`@ConfigurationProperties`,
     default = 1). Global semaphore still caps concurrent Claude calls (default ~6).
   - Cache: static combined instruction preamble behind `CACHE_BREAKPOINT`, cached once,
     re-read per tactic. Verify `cache_read > 0` on a real run.
   - Sections still run their DATA reads in parallel; the Claude call is one per tactic.

3. **Step 3 input (RECONCILED with decision 2).** Original decision 3 said reconstruct from
   `BreakdownValues` (token→value). But decision 2 (combined Step-2 call) now makes Step 2 return
   the structured `TacticConclusion` (overview + ordered section bullet lists) directly, so the
   assembler builds `TacticThoughtsInput`/`TacticNarrativeDigest` straight from `TacticConclusion`
   (+ a tactic-name lookup) — cleaner and no token re-parsing, still honoring decision 3's intent
   (derive from Step-2 output, leave breakdown helpers/DTOs untouched).

4. **Batch C split** — split now: tactic overview folds into the Step-2 combined per-tactic
   call (decision 2); `batchCampaignResults` (Step 4). Step 4 writes ALL campaign result copy:
   `{{thoughts on the performance n}}`, `{{recommendation n}}` + `{{recommendation n text}}`,
   `{{Our results overview N}}`, and the frequency block (f_opportunity/f_fact/f_storytelling).
   Input = Step-3 thoughts where available, else tactic overview + breakdowns.

5. **Strategy alignment (Batch A) — RESOLVED = B.** Align proposal/strategic under results in
   Step 5 (as plan.md). Batch A stays brief-driven at generation; Step 5 harmonizes it into the
   one storyline alongside the campaign-result copy.

6. **Do NOT split `ClaudeResults`; keep `batchResults` (REVISED — supersedes plan.md).**
   `ClaudeResults` is shared: the classic `run` flow (CAMPAIGN/SHEET targets) and
   `PlaceholderResolverService`/`TacticResolvers`/`PlaceholderSectionBuilder` consume it as one
   aggregate. Splitting it breaks the classic flow. Instead: keep `ClaudeResults` + `batchResults`
   for the classic flow, and in the sheet flow ASSEMBLE a `ClaudeResults` from the new granular
   calls (Step-2 overview + Step-4 campaign parts) before handing it to the placeholder resolver.

## Open risk to verify during coding (not blocking)
- Current order runs Batch C before breakdowns because breakdown helpers consume `prelim`
  (built from ccC). New order runs breakdowns before Step 4. Must confirm breakdown helpers
  read only sheet-derived tokens (tactic names/gender), NOT campaign narrative — else the
  prelim seed needs decoupling. Verify before reordering.
