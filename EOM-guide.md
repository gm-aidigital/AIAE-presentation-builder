# How to build an EOM report

https://presentation-builder-poc.replit.app/

## Set your expectations first

Same deal as EOC: this tool will never build your whole deck for you — and it's not meant to. It generates a solid base deck (the standard slides and breakdowns) and writes the narrative to go with them. Treat it like a report you handed to an intern: you're going to review it either way, but reviewing is faster than building from scratch.

One extra thing to keep in mind for EOM. An end-of-month deck reports on a campaign that is **still running**. Every number in it is a *pacing* figure — this month's delivery against this month's target — not a final result. The narrative is written that way too. If you catch a sentence that reads like a wrap-up ("the campaign delivered…", "we recommend for next time…"), fix it before you send.

---

## What's different from EOC — the short version

| | EOC | EOM |
|---|---|---|
| Reporting window | the whole flight | **one month** — defaults to last full month, trimmed to the days the campaign ran |
| Market Volume | required | **not asked for** — EOM decks have no market-share slide |
| Pacing & rates step | — | **new, required**: monthly budget + buy type + rate for every tactic |
| Where plan numbers come from | the media plan's planned budgets/KPIs | **the budget and rate you enter** in the Pacing step |
| Frequency slide | included | **removed from the deck** |
| Awareness & Market Share slide | included | **removed from the deck** |
| Monthly pacing chart | the flight | **every month since campaign start** (daily charts stay on the reporting month) |
| Narrative tone | finished campaign, past tense, recommendations | live flight, gaps read as pacing, reassurance about the remaining months |
| Breakdowns | manual on the Breakdowns tab | identical — still manual |

Everything not listed there works exactly as it does for EOC.

---

## Before you start

Have these **4** things ready (EOC's Market Volume is not one of them — it isn't used):

* **The RFP / brief** — the text describing the campaign (client, goals, audience, budget, flight dates, KPIs, channels). You paste this in.
* **The Media Plan** — a Google Sheet link. For EOM this matters more than it does for EOC: the app reads each tactic's **Rate Type**, **Unit Price**, **Flight End** and plan spend from it, and those pre-fill the Pacing step.
* **The Elevate row data** — a Google Sheet link. The real delivery plus the Line IDs. Flight dates are auto-detected from here.
* **The Change Log** — technically optional, and nowhere in this tool does it matter more than here. Drop in everything that happened over the past month: budget shifts, a tactic that launched late, an audience re-weighting, creative swaps, a client request that changed the plan mid-flight. You can also paste last month's report — the conclusions and recommendations, in whatever free-form shape you have them. There's no format to follow and no length limit worth worrying about. The more context you put in, the sharper and more grounded the conclusions and recommendations come out; leave it empty and the tool can only describe what the numbers did, never why.

Both sheets must be Google Sheets (`docs.google.com/spreadsheets/…`) and shared so the app can read them. No Excel, no CSV, no uploads.

**Also worth having open:** last month's deck. The tool gets a real month-over-month delivery table, so the *direction* of the movements in the strategic insights is grounded in the data — but the *explanation* for each movement is inferred from your brief and change log. Last month's deck is how you catch an explanation that contradicts what you already told the client.

---

## Walk through it

### 1. Choose the report type

Pick **EOM** and press Continue. EOM and EOC are both live; Agenda and Excel are greyed out with a *Soon* tag — they're not built yet.

### 2. Campaign data

Fill in the inputs. The panel on the right shows what's still Waiting.

* **RFP / Campaign Brief** *required* — paste the brief.
* **Change Log** *optional, but read this* — on a monthly report this is the most valuable optional field in the tool. Budget shifts, a tactic that launched late, an audience re-weighting, a creative swap mid-month — plus last month's conclusions and recommendations pasted in as free text. This is what lets the narrative explain *why* the month moved instead of guessing, and it's the only place you can hand the tool that context. On EOC you can skip it; on EOM don't.
* **Market Volume** — **not shown.** The EOM deck carries no market-volume slide, so the field is hidden and not required. Nothing to do here.
* **Estimate dayparting & gender split (AI)** — a toggle, on by default. Works exactly as it does on EOC: dayparting and gender aren't reliably tracked DSP-side, so the tool estimates them. Turn it off to leave those cells blank (`—`) and type your own values into the sheet.
* **Media Plan** *required* — paste the link, press Connect. The app finds the `Proposal` or `Estimates` tab automatically. Only standard AIDigital media-plan templates are supported.
* **Elevate row data** *required* — paste the link, press Connect. Only the standard dashboard export layout is supported. If you corrected delivery numbers, do it through the Adjustment tool, not by hand-editing rows.

**Flight dates — this is the one that catches people.**

The app detects the campaign range from the Elevate data, then narrows it to **the last full calendar month**, trimmed to the days the campaign was actually live. Open the tool on 3 August for a campaign that started 17 July, and you get **17–31 July**. Open it for a campaign that's been running since April, and you get **1–31 July**.

A hint under the field tells you this is what happened. Change the dates if this deck covers a different period (reporting a month late, a mid-month client review, a custom window). Once you type a date yourself, the app stops suggesting.

Two things to be clear about, because everything downstream depends on them:

* These dates are the **reporting window**, not the campaign flight. Every actual figure in the deck is what delivered inside this window.
* The Elevate sheet still holds the whole campaign. The app slices it — you don't need to trim the source data.

**Then: Match line items.** Same as EOC. When both sheets are connected, the Match box appears. It auto-matches what it can; drag the right ID onto the tactics it missed, then Confirm Mapping. Every tactic needs a Line ID, and your naming convention has to be correct or the ID won't be found.

**Then: Pacing & rates — EOM only, and required.**

A second box appears after matching: *Pacing & rates — needs input*. Press **Set pacing**. You get one row per tactic with four columns.

* **Monthly budget** — the budget being paced this month for that tactic. **This is the single most important number you type in the whole flow.** It is the plan for the reporting month: the deck's plan spend for the tactic *is* this figure, and its planned impressions / clicks / views are this figure divided by the rate. Get it wrong and every pacing number on that tactic's slides is wrong.
* **Buy type** — CPM / CPC / CPV, read from the media plan's *Rate Type* column and shown read-only ("from plan"). If the plan has no Rate Type for that tactic, you get a dropdown instead — pick one.
* **Rate** — pre-filled from the media plan's *Unit Price*, and editable. Overwrite it if the final negotiated rate differs from what the plan says. Under the field the tool shows what the budget buys at that rate (`≈ 1.2M imps`) — a fast sanity-check that you didn't type a CPM where a CPC belongs.
* Under the tactic name you'll see the plan's own figures (`plan: $1,500 · 250K units`) for reference.

**The "Evenly paced" shortcut.** Instead of typing every budget by hand, tick **Evenly paced** and the tool derives them: it takes each tactic's full-flight plan spend, spreads it evenly over the days the tactic is *actually* live — first day with real delivery in the Elevate data through the media plan's Flight End — and bills the reporting window for the days it covers. The hint on each row shows the working: `120d × $83.33 → 30d`.

Note it uses the **real** first delivery day, not the planned one. A tactic that launched two days late still has to spend its whole plan, so its daily rate is a little higher than the media plan implies. That's deliberate.

Two things about the toggle:
* It needs a Flight End in the media plan, matched line items with delivery in the raw data, and confirmed flight dates. Without those it's disabled and the note tells you what's missing.
* **Your typed numbers win.** Type into a budget cell while Evenly paced is on and the toggle switches off, keeping the derived numbers as your starting point — so you can take the even spread and then correct one tactic. Switching the toggle off by hand restores whatever you'd typed before you switched it on.

You can't leave this step until every tactic has a budget, a buy type and a rate. The footer counts them for you (`7/9 tactics ready`), and the wizard won't advance past step 2 until you press **Confirm pacing**.

### 3. Breakdowns per tactic

Identical to EOC. Per tactic, flip on any of the 5 sections you want: **Top Publishers**, **Creative Analysis**, **Geo Performance**, **Audience Analysis**, **Device Breakdown**. Press **Build the sheet**.

The deck supports a maximum of **28 tactics**. Beyond that, only the first 28 make it in.

A practical note for monthly reporting: whatever you switch on, you're typing its numbers by hand *every month*. Pick the sections the client actually reads and keep the same set month to month, so the decks stay comparable.

### 4. Review the generated sheet

The app builds one Google Sheet from the **EOM template** — a different workbook from the EOC one, with the extra pacing columns. Open it with **Open in Sheets**, check the numbers, fill anything blank. Anything blank in the sheet comes out blank in the deck.

What to check that's specific to EOM:

* **Unit rate** and **Rate type** columns — these are what you entered in the Pacing step. Confirm they landed on the right rows.
* **Plan** columns — the planned figure is shown in the unit the tactic is actually bought in (impressions for CPM, clicks for CPC, views for CPV), derived from your monthly budget ÷ rate. If a plan number looks an order of magnitude off, the culprit is almost always the budget or the rate, not the sheet.
* **Fact** columns — the month's real delivery. Spend keeps its cents.
* **Frequency** — on EOM it's derived from the media plan's per-week frequency column when that's available. The frequency *slide* is dropped from the deck, so this matters less than on EOC, but the per-tactic figure still shows up in tables.

Edited something? Press **Refresh** to pull your changes back into the app. Then press **Confirm — it's correct**.

**Breakdown data is filled in by hand**, exactly as on EOC. Open the sheet's `Breakdowns` tab and fill every cell shaded lime green 🍋‍🟩 — publisher names, impressions, share of voice, geo/market rows, creative CTR/VCR/spend, audience age and gender splits, for each tactic block. Anything **not** lime (the blue section headers, the `{{tactic n …}}` tokens) is a label the app reads — leave those alone. Blank lime cells = empty breakdown slides. You must tick "I've filled in the breakdown data" before you can confirm.

One EOM-specific trap: the breakdown numbers must be **this month's**, not campaign-to-date. If you copy last month's block forward and only update a couple of rows, you'll ship a slide that quietly reports the wrong period. Pull the figures fresh from the DSP for the reporting window.

### 5. Generate

Press **Generate report** and watch the 4 stages: Reading sheet → Writing narrative → Building deck → Building charts.

When it's done, press **Open report**. The deck is a Google Slides file, shared with the team automatically.

What the EOM deck does differently:

* **The Frequency & Velocity slide is removed.** It's an end-of-campaign story; it doesn't hold up on a month of a live flight.
* **The Awareness & Market Share slide is removed.** That's why Market Volume was never asked for.
* **The monthly pacing chart covers every month since the campaign started**, so the client sees the trend, not a single dot. The daily charts stay inside the reporting month.
* **The narrative is written mid-flight.** Strategic insights are asked for as month-over-month movements; a gap against plan is presented as a pacing gap with months left to close it, and the per-tactic close is why the tactic is on track — not a fix for a failure and not a verdict.

Warnings can still appear (a chart that didn't build, a breakdown slide with no bullets). The report is still created — just check those slides.

---

## What to review before you send

Everything in the EOC guide applies. On top of it, for EOM:

1. **The reporting window on the title and pacing slides.** Wrong month = wrong deck, and it's the easiest thing to miss because everything else still looks plausible.
2. **Every plan figure.** On EOC the plan comes from the media plan; on EOM it comes from the budget and rate *you* typed. There's nothing to cross-check it against except your own judgement.
3. **Tense.** The prompts push hard for mid-flight language, but this is generated text. Any sentence that reads like a wrap-up needs rewriting.
4. **Month-over-month claims.** The narrative will describe movement between months. Open last month's deck and confirm the direction and rough size are right.
5. **Breakdown periods.** See above — this month's numbers, not cumulative ones.

And generally: check every number and every recommendation before you send. This is generated by software, and software gets things wrong. The deck is a strong first draft, not a final answer — your review is what makes it client-ready.

---

## Appendix

### What formats are supported

| Input | Format | Where it comes from |
|---|---|---|
| Media Plan (MP) | Google Sheet link only | The planning sheet. App reads the `Proposal` or `Estimates` tab. On EOM it also supplies each tactic's Rate Type, Unit Price and Flight End for the Pacing step. Column headers can vary — Geo can be "Geo / Targeted Locations / Market", Funnel can be "Goal / Objective / Stage". |
| Elevate row data | Google Sheet link only | The delivery export from Elevate. Real numbers and Line IDs. Flight dates and each tactic's first delivery day are detected from here. |
| Brief / RFP | Plain text you type | Paste it from the RFP or brief document. |
| Change Log | Plain text you type or paste | Optional, but the highest-value optional field on a monthly report. Mid-flight changes, plus last month's conclusions and recommendations in free form. Any format, the more the better. |
| Monthly budget, buy type, rate | Typed per tactic in the Pacing step | Budget is yours to enter (or derive with "Evenly paced"); buy type and rate are pre-filled from the media plan and the rate is editable. |
| Market Volume | — | **Not used on EOM.** |
| Breakdown data | Typed into the sheet's `Breakdowns` tab | You fill it in by hand in step 4. Only needed for sections you switched on. |

### Don't get caught out

**Links only.** Only Google Sheet links work. No uploads, no Excel, no CSV. The sheet must be shared or the app can't open it.

**Check the month before anything else.** The dates are a *suggestion* — last full month, clipped to the campaign's live days. Correct them before you match line items, because the Evenly-paced budgets are computed against that window.

**The monthly budget is the plan.** Not the media plan's monthly line — whatever you type. Every plan figure, every pacing percentage and every "on track" sentence in the deck follows from it.

**Evenly paced ≠ the media plan divided by months.** It divides by the days the tactic is *really* live, starting from its first delivery in the raw data. A late launch spends faster.

**Rebuild wipes edits.** Pressing Rebuild sheet in step 3 makes a fresh sheet and throws away edits in the old one. It can't be undone.

**Breakdowns are manual, every month.** Switch a section on and you type its numbers yourself, for this month specifically. Forget, and those slides come out empty.

**Refresh after editing.** Fix numbers in Google Sheets, then press Refresh in the app so it re-reads them. Otherwise it uses the old values.

**28 tactics max.** Media plans with more get cut to the first 28. Trim or reorder if the important tactics sit past number 28.

**Match before you continue.** Every tactic needs a Line ID or its real numbers won't join up — and on EOM, no Line ID also means no first-delivery date, which means no Evenly-paced budget for that row.

That's the whole flow: **Type → Data (+ Pacing) → Breakdowns → Review sheet → Generate.** The Google Sheet in the middle is always the source of truth — get it right there and the deck follows.

https://presentation-builder-poc.replit.app/
