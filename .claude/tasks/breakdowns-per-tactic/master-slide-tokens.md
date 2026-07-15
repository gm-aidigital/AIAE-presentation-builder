# Breakdown master-slide tokens (byte-exact)

Source: `Template.pptx` (5 master slides, extracted 2026-07-15 by concatenating
`<a:t>` runs within each paragraph — PowerPoint splits tokens across runs).

Slide order in the master file → breakdown type → wire code (`BreakdownType`):

| PPTX slide | Type | Code | Title text |
|---|---|---|---|
| slide1 | Top Publishers | `tp` | `DELIVERY BREAKDOWN – {{tactic n}}` / `TOP 15 PUBLISHERS` |
| slide2 | Creative analysis | `ca` | `DELIVERY BREAKDOWN – {{tactic n}}` / `CREATIVE ANALYSIS` |
| slide3 | Geo performance | `geo` | `AUDIENCE FOOTPRINT – {{tactic n}}` / `GEOGRAPHIC PERFORMANCE` |
| slide4 | Audience analysis | `aud` | `WHO WE REACHED – {{tactic n}}` / `AUDIENCE ANALYSIS` |
| slide5 | Device breakdown | `dev` | `HOW AUDIENCES ENGAGED - {{tactic n}}` / `DEVICE BREAKDOWN` |

The variable `n` is the tactic number. On the masters it is always a standalone
token delimited by one of `_`, `.`, space, `{`, `}`. Renumber rule (Java regex):
`(?<=[_.\s{])n(?=[_.\s}])` → replace with the tactic number. Applied per-token,
scoped to the duplicated slide (see plan / design note).

---

## slide1 — Top Publishers (`tp`)

```
{{tactic n}}
{{publisher_n.1}}  {{pub_imp_n.1}}  {{pub_sov_n.1}}
{{publisher_n.2}}  {{pub_imp_n.2}}  {{pub_sov_n.2}}
{{publisher_n.3}}  {{pub_imp_n.3}}  {{pub_sov_n.3}}
{{publisher_n.4}}  {{pub_imp_n.4}}  {{pub_sov_n.4}}
{{publisher_n.5}}  {{pub_imp_n.5}}  {{pub_sov_n.5}}
{{publisher_n.6}}  {{pub_imp_n.6}}  {{pub_sov_n.6}}
{{publisher_n.7}}  {{pub_imp_n.7}}  {{pub_sov_n.7}}
{{publisher_n.8}}  {{pub_imp_n.8}}  {{pub_sov_n.8}}
{{publisher_n.9}}  {{pub_imp_n.9}}  {{pub_sov_n.9}}
{{publisher_n.10}} {{pub_imp_n.10}} {{pub_sov_n.10}}
{{publisher_n.11}} {{pub_imp_n.11}} {{pub_sov_n.11}}
{{publisher_n.12}} {{pub_imp_n.12}} {{pub_sov_n.12}}
{{publisher_n.13}} {{pub_imp_n.13}} {{pub_sov_n.13}}
{{publisher_n.14}} {{pub_imp_n.14}} {{pub_sov_n.14}}
{{publisher_n.15}} {{pub_imp_n.15}} {{pub_sov_n.15}}
{{tactic n imps}}          (TOTAL row)
{{publishers_observation_n_1}}   (KEY OBSERVATIONS — n-indexed, updated 2026-07-15)
{{publishers_observation_n_2}}
{{publishers_observation_n_3}}
{{publishers_observation_n_4}}
```
Columns: `#` | PUBLISHER | IMPRESSIONS | SHARE OF VOICE.
All publisher tokens now carry `n` (observations updated to `_n_` form), so every
token renumbers cleanly per tactic — no shared/global-collision tokens remain.

## slide2 — Creative analysis (`ca`)

```
{{tactic n}}
{{cr_live_n}}                    (CREATIVES LIVE)
{{tactic n KPI type}}  {{cr_bKPI_n}}   (BEST <KPI>, value "%")
{{tactic n KPI type}}  {{cr_aKPI_n}}   (AVG. <KPI>, value "%")
{{tactic n top creative name}}         (TOP CREATIVE)

table rows (CREATIVE | IMPRESSIONS | CTR | VCR | SPEND):
row1: {{tactic n top creative name n.1}} {{tactic n}} {{tactic n.1 top creative imps}} {{tactic n.1 top creative ctr}} {{tactic n.1 top creative vcr}} {{tactic n.1 top creative spend}}
row2: {{tactic n top creative name n.2}} {{tactic n}} {{tactic n.2 top creative imps}} {{tactic n.2 top creative ctr}} {{tactic n.2 top creative vcr}} {{tactic n.2 top creative spend}}
row3: {{tactic n top creative name n.3}} {{tactic n}} {{tactic n.3 top creative imps}} {{tactic n.3 top creative ctr}} {{tactic n.3 top creative vcr}} {{tactic n.3 top creative spend}}
row4: {{tactic n top creative name n.4}} {{tactic n}} {{tactic n.4 top creative imps}} {{tactic n.4 top creative ctr}} {{tactic n.4 top creative vcr}} {{tactic n.4 top creative spend}}
row5: {{tactic n top creative name n.5}} {{tactic n}} {{tactic n.5 top creative imps}} {{tactic n.5 top creative ctr}} {{tactic n.5 top creative vcr}} {{tactic n.5 top creative spend}}

{{cr_takeaway_tactic n_1}}   (KEY TAKEAWAYS)
{{cr_takeaway_tactic n_2}}
{{cr_takeaway_tactic n_3}}
{{cr_takeaway_tactic n_4}}
```
(Template typos fixed 2026-07-15: rows 2–5 now carry the proper `...ctr}}` token,
and takeaways use the corrected `cr_takeaway` spelling.)

## slide3 — Geo performance (`geo`)

```
{{tactic n}}
{{geo_n_amount}}     (MARKETS ACTIVATED)
{{geo_n_topgeo}}     (TOP GEO)
{{tactic n KPI type}} {{geo_n_topkpi}}   (MOST EFFICIENT <KPI>)

table rows (GEO | IMPRESSIONS SHARE | IMPS?/ {{tactic n KPI type}}):
1: {{geo_n.1}} {{geo_imp_n.1}} {{geo_kpi_n.1}}
2: {{geo_n.2}} {{geo_imp_n.2}} {{geo_kpi_n.2}}
3: {{geo_n.3}} {{geo_imp_n.3}} {{geo_kpi_n.3}}
4: {{geo_n.4}} {{geo_imp_n.4}} {{geo_kpi_n.4}}
5: {{geo_n.5}} {{geo_imp_n.5}} {{geo_kpi_n.5}}
6: {{geo_n.6}} {{geo_imp_n.6}} {{geo_kpi_n.6}}
7: {{geo_n.7}} {{geo_imp_n.7}} {{geo_kpi_n.7}}
8: {{geo_n.8}} {{geo_imp_n.8}} {{geo_kpi_n.8}}

{{geo_insight_n.1}}  {{geo_insight_n.2}}
{{geo_insight_n.3}}  {{geo_insight_n.4}}
{{geo_n_reco}}       (Recommendation: ...)
```
(Template typo fixed 2026-07-15: row 4 is now `{{geo_kpi_n.4}}`.)

## slide4 — Audience analysis (`aud`)

```
{{tactic n}}
{{age_n_gr}}      (AGE DISTRIBUTION)
{{gender_n}}      (GENDER DEMOGRAPHICS)
{{aud_n_1}}  {{aud_in_n_1}}   (TOP SEGMENT + "<idx> INDEX")
{{tactic n male}}    (GENDER SPLIT — MALE)
{{tactic n female}}  (GENDER SPLIT — FEMALE)

table rows (SEGMENT | AFFINITY INDEX):
1: {{aud_n_1}} {{aud_in_n_1}}
2: {{aud_n_2}} {{aud_in_n_2}}
3: {{aud_n_3}} {{aud_in_n_3}}
4: {{aud_n_4}} {{aud_in_n_4}}
5: {{aud_n_5}} {{aud_in_n_5}}

{{aud_n_takeaway}}   (KEY TAKEAWAY)
{{aud_n_worked}}     (WHAT WORKED)
{{aud_n_flag}}       (WATCH-OUTS)
{{aud_n_reco}}       (RECOMMENDED ACTION)
```

## slide5 — Device breakdown (`dev`)

```
{{tactic n}}
{{dev_n_ctr}}        (HIGHEST CTR, value "%")
{{dev_n_vcr}}        (BEST COMPLETION, value "%")
{{dev_n_amount}}     (DEVICES TRACKED)
{{top_dev_n}}        (TOP DEVICE)
{{dev_proc_imps_n}}  ("... OF IMPRESSIONS")

table rows (DEVICE | IMPRESSIONS | CTR | VCR | SPEND); device labels are literal:
Mobile:       {{mobile_imps_n}}  {{mobile_ctr_n}}  {{mobile_vcr_n}}  {{mobile_spend_n}}
Connected TV: {{ctv_imps_n}}     —(literal)        {{ctv_vcr_n}}     {{ctv_spend_n}}
Desktop:      {{desktop_imps_n}} {{desktop_ctr_n}} {{desktop_vcr_n}} {{desktop_spend_n}}
Tablet:       {{tablet_imps_n}}  {{tablet_ctr_n}}  {{tablet_vcr_n}}  {{tablet_spend_n}}

{{dev_n_takeaway}}   (KEY TAKEAWAY)
{{dev_n_worked}}     (WHAT WORKED)
{{dev_n_flag}}       (WATCH-OUTS)
{{dev_n_reco}}       (RECOMMENDED ACTION)
```

---

## Master slide object-ids (TO FILL — from presentations.get on live template)
```
tp  -> <slideObjectId>
ca  -> <slideObjectId>
geo -> <slideObjectId>
aud -> <slideObjectId>
dev -> <slideObjectId>
```
Note: these master slides are NOT yet in the live Slides template
(`11qzOC7…`) — they exist only in Template.pptx. They must be added to the live
deck and their page object-ids collected before the config is wired.

## Design decision (n-renumber approach)
Duplicate master → scoped `replaceAllText(pageObjectIds=[copy])` → position after
`{{tactic N}}` slide. Scope is what prevents cross-copy override (two copies share
identical generic tokens).

**Correction (2026-07-15): value fill can NOT be deferred to the global placeholder
map.** This note previously claimed that once renumbered, concrete tokens
(`{{publisher_3.1}}`) would be filled by the existing pass. They are not: the order in
`ReportGenerationServiceImpl.runSlidesFromSheet` is `createDeck` (which runs the only
placeholder pass) *then* `addBreakdownSlides` (which duplicates the masters). The copies
do not exist when the pass runs, so their tokens would ship raw.

Actual behaviour: `addBreakdownSlides` takes a `breakdownValues` map and, per master
token, replaces the generic token with its **final value** in one scoped call when the
renumbered form has an entry; tokens with no entry fall back to a plain renumber (and
would still ship raw — so every token a breakdown slide carries needs a value).
Top Publishers values are assembled by `PublisherBreakdownHelper`.
Every token now carries `n` (publisher observations updated to `_n_` form), so all
renumber cleanly per tactic — no shared/global-collision tokens remain.
