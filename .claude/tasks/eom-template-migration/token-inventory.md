# EOM-шаблон: инвентаризация токенов по слайдам

Источник: `eom-template-dump.txt` (23 слайда). Токены перечислены в порядке появления в XML;
повторяющиеся нумерованные слоты свёрнуты в диапазон `1..N` с фактическим количеством.

| Слайд | Что это | Уник. токенов |
|---|---|---|
| 1 | Cover (client / month / flight / pace) | 8 |
| 2 | Our north star + channels by funnel stage | 8 |
| 3 | Pacing dashboard — тактики 1–7 | 49 |
| 4 | Pacing dashboard — тактики 8–14 | 49 |
| 5 | Pacing dashboard — тактики 15–21 | 49 |
| 6 | Pacing dashboard — тактики 22–28 | 49 |
| 7 | Performance vs plan — тактики 1–7 | 37 |
| 8 | Performance vs plan — тактики 8–14 | 37 |
| 9 | Performance vs plan — тактики 15–21 | 37 |
| 10 | Performance vs plan — тактики 22–28 | 37 |
| 11 | Дивайдер «Channel-by-channel breakdown» | 7 |
| 12 | МАСТЕР тактики #1 (обзор канала, 5 чартов) → копии tct_n | 19 |
| 13 | МАСТЕР тактики #2 (pacing-таблица канала) → копии tct_n | 39 |
| 14 | Дивайдер «Where we go from here» | 2 |
| 15 | What we did this month & why | 18 |
| 16 | Focus — следующий месяц | 12 |
| 17 | Дивайдер «Slides library» (удаляется при сборке) | 0 |
| 18 | МАСТЕР брейкдауна: Top 15 publishers | 51 |
| 19 | МАСТЕР брейкдауна: Creative analysis | 33 |
| 20 | МАСТЕР брейкдауна: Geographic performance | 33 |
| 21 | МАСТЕР брейкдауна: Audience analysis | 25 |
| 22 | МАСТЕР брейкдауна: Device breakdown | 29 |
| 23 | МАСТЕР: Thoughts on tactic performance | 5 |

Всего вхождений: 633.

---

## Слайд 1 — Cover (client / month / flight / pace)
`tbl=False pics=6` · токенов: 8

- `{{client_name}}`
- `{{reporting month}}`
- `{{flight_dates}}`
- `{{mon no}}`
- `{{total mon no}}`
- `{{planned total impressions short}}`
- `{{fact total impressions short}}`
- `{{total imps pace}}`

## Слайд 2 — Our north star + channels by funnel stage
`tbl=False pics=1` · токенов: 8

- `{{audience_segments}}`
- `{{extended north star}}`
- `{{our north star}}`
- `{{horizon}}`
- `{{awareness channels}}`
- `{{consideration channels}}`
- `{{geo_locations}}`
- `{{conversions channels}}`

## Слайд 3 — Pacing dashboard — тактики 1–7
`tbl=True pics=1` · токенов: 49

- `{{tactic 1..7}}` — 7 шт. (tactic 1 … tactic 7)
- `{{tactic 1..7 planned budget}}` — 7 шт. (tactic 1 planned budget … tactic 7 planned budget)
- `{{tactic 1..7 fact budget}}` — 7 шт. (tactic 1 fact budget … tactic 7 fact budget)
- `{{tactic 1..7 pacing}}` — 7 шт. (tactic 1 pacing … tactic 7 pacing)
- `{{tactic 1..7 planned imps}}` — 7 шт. (tactic 1 planned imps … tactic 7 planned imps)
- `{{tactic 1..7 fact imps}}` — 7 шт. (tactic 1 fact imps … tactic 7 fact imps)
- `{{total planned budget}}`
- `{{total fact budget}}`
- `{{total pacing}}`
- `{{total planned imps}}`
- `{{total fact imps}}`
- `{{mon no}}`
- `{{pacing dash takeaway 1}}`

## Слайд 4 — Pacing dashboard — тактики 8–14
`tbl=True pics=1` · токенов: 49

- `{{tactic 8..14}}` — 7 шт. (tactic 8 … tactic 14)
- `{{tactic 8..14 planned budget}}` — 7 шт. (tactic 8 planned budget … tactic 14 planned budget)
- `{{tactic 8..14 fact budget}}` — 7 шт. (tactic 8 fact budget … tactic 14 fact budget)
- `{{tactic 8..14 pacing}}` — 7 шт. (tactic 8 pacing … tactic 14 pacing)
- `{{tactic 8..14 planned imps}}` — 7 шт. (tactic 8 planned imps … tactic 14 planned imps)
- `{{tactic 8..14 fact imps}}` — 7 шт. (tactic 8 fact imps … tactic 14 fact imps)
- `{{total planned budget}}`
- `{{total fact budget}}`
- `{{total pacing}}`
- `{{total planned imps}}`
- `{{total fact imps}}`
- `{{mon no}}`
- `{{pacing dash takeaway 2}}`

## Слайд 5 — Pacing dashboard — тактики 15–21
`tbl=True pics=1` · токенов: 49

- `{{tactic 15..21}}` — 7 шт. (tactic 15 … tactic 21)
- `{{tactic 15..21 planned budget}}` — 7 шт. (tactic 15 planned budget … tactic 21 planned budget)
- `{{tactic 15..21 fact budget}}` — 7 шт. (tactic 15 fact budget … tactic 21 fact budget)
- `{{tactic 15..21 pacing}}` — 7 шт. (tactic 15 pacing … tactic 21 pacing)
- `{{tactic 15..21 planned imps}}` — 7 шт. (tactic 15 planned imps … tactic 21 planned imps)
- `{{tactic 15..21 fact imps}}` — 7 шт. (tactic 15 fact imps … tactic 21 fact imps)
- `{{total planned budget}}`
- `{{total fact budget}}`
- `{{total pacing}}`
- `{{total planned imps}}`
- `{{total fact imps}}`
- `{{mon no}}`
- `{{pacing dash takeaway 3}}`

## Слайд 6 — Pacing dashboard — тактики 22–28
`tbl=True pics=1` · токенов: 49

- `{{tactic 22..28}}` — 7 шт. (tactic 22 … tactic 28)
- `{{tactic 22..28 planned budget}}` — 7 шт. (tactic 22 planned budget … tactic 28 planned budget)
- `{{tactic 22..28 fact budget}}` — 7 шт. (tactic 22 fact budget … tactic 28 fact budget)
- `{{tactic 22..28 pacing}}` — 7 шт. (tactic 22 pacing … tactic 28 pacing)
- `{{tactic 22..28 planned imps}}` — 7 шт. (tactic 22 planned imps … tactic 28 planned imps)
- `{{tactic 22..28 fact imps}}` — 7 шт. (tactic 22 fact imps … tactic 28 fact imps)
- `{{total planned budget}}`
- `{{total fact budget}}`
- `{{total pacing}}`
- `{{total planned imps}}`
- `{{total fact imps}}`
- `{{mon no}}`
- `{{pacing dash takeaway 4}}`

## Слайд 7 — Performance vs plan — тактики 1–7
`tbl=True pics=1` · токенов: 37

- `{{mon no}}`
- `{{tactic 1..7}}` — 7 шт. (tactic 1 … tactic 7)
- `{{tactic 1..7 KPI type}}` — 7 шт. (tactic 1 KPI type … tactic 7 KPI type)
- `{{tactic 1..7 KPI goal}}` — 7 шт. (tactic 1 KPI goal … tactic 7 KPI goal)
- `{{tactic 1..7 KPI}}` — 7 шт. (tactic 1 KPI … tactic 7 KPI)
- `{{tactic 1..7 vs goal}}` — 7 шт. (tactic 1 vs goal … tactic 7 vs goal)
- `{{performance dash takeaway 1}}`

## Слайд 8 — Performance vs plan — тактики 8–14
`tbl=True pics=1` · токенов: 37

- `{{mon no}}`
- `{{tactic 8..14}}` — 7 шт. (tactic 8 … tactic 14)
- `{{tactic 8..14 KPI type}}` — 7 шт. (tactic 8 KPI type … tactic 14 KPI type)
- `{{tactic 8..14 KPI goal}}` — 7 шт. (tactic 8 KPI goal … tactic 14 KPI goal)
- `{{tactic 8..14 KPI}}` — 7 шт. (tactic 8 KPI … tactic 14 KPI)
- `{{tactic 8..14 vs goal}}` — 7 шт. (tactic 8 vs goal … tactic 14 vs goal)
- `{{performance dash takeaway 2}}`

## Слайд 9 — Performance vs plan — тактики 15–21
`tbl=True pics=1` · токенов: 37

- `{{mon no}}`
- `{{tactic 15..21}}` — 7 шт. (tactic 15 … tactic 21)
- `{{tactic 15..21 KPI type}}` — 7 шт. (tactic 15 KPI type … tactic 21 KPI type)
- `{{tactic 15..21 KPI goal}}` — 7 шт. (tactic 15 KPI goal … tactic 21 KPI goal)
- `{{tactic 15..21 KPI}}` — 7 шт. (tactic 15 KPI … tactic 21 KPI)
- `{{tactic 15..21 vs goal}}` — 7 шт. (tactic 15 vs goal … tactic 21 vs goal)
- `{{performance dash takeaway 3}}`

## Слайд 10 — Performance vs plan — тактики 22–28
`tbl=True pics=1` · токенов: 37

- `{{mon no}}`
- `{{tactic 22..28}}` — 7 шт. (tactic 22 … tactic 28)
- `{{tactic 22..28 KPI type}}` — 7 шт. (tactic 22 KPI type … tactic 28 KPI type)
- `{{tactic 22..28 KPI goal}}` — 7 шт. (tactic 22 KPI goal … tactic 28 KPI goal)
- `{{tactic 22..28 KPI}}` — 7 шт. (tactic 22 KPI … tactic 28 KPI)
- `{{tactic 22..28 vs goal}}` — 7 шт. (tactic 22 vs goal … tactic 28 vs goal)
- `{{performance dash takeaway 4}}`

## Слайд 11 — Дивайдер «Channel-by-channel breakdown»
`tbl=False pics=3` · токенов: 7

- `{{mon no}}`
- `{{total mon no}}`
- `{{reporting month}}`
- `{{flight_dates}}`
- `{{tactic 1..3}}` — 3 шт. (tactic 1 … tactic 3)

## Слайд 12 — МАСТЕР тактики #1 (обзор канала, 5 чартов) → копии tct_n
`tbl=False pics=5` · токенов: 19

- `{{tactic n}}`
- `{{tactic n overview}}`
- `{{tactic n imps}}`
- `{{tactic n KPI type}}`
- `{{tactic n spend}}`
- `{{tactic n f}}`
- `{{tactic n reach}}`
- `{{tactic n volume}}`
- `{{tactic n weekdays}}`
- `{{tactic n weekends}}`
- `{{tactic n male}}`
- `{{tactic n female}}`
- `{{tactic n top creative imps}}`
- `{{tactic n top creative clicks}}`
- `{{tactic n top creative name}}`
- `{{tactic n goal}}`
- `{{tactic n KPI}}`
- `{{tactic n contr}}`
- `{{tactic n other contr}}`

## Слайд 13 — МАСТЕР тактики #2 (pacing-таблица канала) → копии tct_n
`tbl=True pics=1` · токенов: 39

- `{{actions pacing n}}`
- `{{what worked pacing n}}`
- `{{watch outs pacing n}}`
- `{{tactic n}}`
- `{{tactic n goal}}`
- `{{tactic n planned imps}}`
- `{{tactic n fact imps}}`
- `{{tactic n imps pacing}}`
- `{{tactic n eoc planned imps}}`
- `{{tactic n proj imps}}`
- `{{tactic n ctr plan}}`
- `{{tactic n ctr}}`
- `{{tactic n ctr pacing}}`
- `{{tactic n ctr proj}}`
- `{{tactic n clicks plan}}`
- `{{tactic n clicks}}`
- `{{tactic n clicks pacing}}`
- `{{tactic n clicks mp}}`
- `{{tactic n clicks proj}}`
- `{{tactic n reach plan}}`
- `{{tactic n reach}}`
- `{{tactic n reach pacing}}`
- `{{tactic n reach plan eoc}}`
- `{{tactic n reach proj}}`
- `{{tactic n planned cpm}}`
- `{{tactic n fact cpm}}`
- `{{tactic n cpm pacing}}`
- `{{tactic n cpm proj}}`
- `{{tactic n planned budget}}`
- `{{tactic n fact budget}}`
- `{{tactic n budget pacing}}`
- `{{tactic n spend plan}}`
- `{{tactic n spend proj}}`
- `{{mon no}}`
- `{{tactic n planned imps short}}`
- `{{tactic n fact imps short}}`
- `{{tactic n fact budget short}}`
- `{{tactic n vs goal}}`
- `{{pacing n next month}}`

## Слайд 14 — Дивайдер «Where we go from here»
`tbl=False pics=3` · токенов: 2

- `{{mon no}}`
- `{{total mon no}}`

## Слайд 15 — What we did this month & why
`tbl=False pics=1` · токенов: 18

- `{{observation 1..3}}` — 3 шт. (observation 1 … observation 3)
- `{{observation 1..3 text}}` — 3 шт. (observation 1 text … observation 3 text)
- `{{action 1..3}}` — 3 шт. (action 1 … action 3)
- `{{action 1..3 text}}` — 3 шт. (action 1 text … action 3 text)
- `{{impact 1..3}}` — 3 шт. (impact 1 … impact 3)
- `{{impact 1..3 text}}` — 3 шт. (impact 1 text … impact 3 text)

## Слайд 16 — Focus — следующий месяц
`tbl=False pics=1` · токенов: 12

- `{{reporting month +1}}`
- `{{mon no +1}}`
- `{{updated projection}}`
- `{{carry forward 1..3}}` — 3 шт. (carry forward 1 … carry forward 3)
- `{{pivot 1..3}}` — 3 шт. (pivot 1 … pivot 3)
- `{{test 1..3}}` — 3 шт. (test 1 … test 3)

## Слайд 17 — Дивайдер «Slides library» (удаляется при сборке)
`tbl=False pics=0` · токенов: 0

(токенов нет — только статика)

## Слайд 18 — МАСТЕР брейкдауна: Top 15 publishers
`tbl=True pics=1` · токенов: 51

- `{{publishers_observation_n_1..4}}` — 4 шт. (publishers_observation_n_3 … publishers_observation_n_4)
- `{{tactic n}}`
- `{{publisher_n.1..15}}` — 15 шт. (publisher_n.1 … publisher_n.15)
- `{{pub_imp_n.1..15}}` — 15 шт. (pub_imp_n.1 … pub_imp_n.15)
- `{{pub_sov_n.1..15}}` — 15 шт. (pub_sov_n.1 … pub_sov_n.15)
- `{{tactic n imps}}`

## Слайд 19 — МАСТЕР брейкдауна: Creative analysis
`tbl=True pics=1` · токенов: 33

- `{{cr_takeaway_tactic n_1..4}}` — 4 шт. (cr_takeaway_tactic n_3 … cr_takeaway_tactic n_1)
- `{{cr_live_n}}`
- `{{cr_bKPI_n}}`
- `{{tactic n top creative name}}`
- `{{tactic n}}`
- `{{tactic n top creative name n.1..5}}` — 5 шт. (tactic n top creative name n.1 … tactic n top creative name n.5)
- `{{tactic n.1..5 top creative imps}}` — 5 шт. (tactic n.1 top creative imps … tactic n.5 top creative imps)
- `{{tactic n.1..5 top creative ctr}}` — 5 шт. (tactic n.1 top creative ctr … tactic n.5 top creative ctr)
- `{{tactic n.1..5 top creative vcr}}` — 5 шт. (tactic n.1 top creative vcr … tactic n.5 top creative vcr)
- `{{tactic n.1..5 top creative spend}}` — 5 шт. (tactic n.1 top creative spend … tactic n.5 top creative spend)

## Слайд 20 — МАСТЕР брейкдауна: Geographic performance
`tbl=True pics=1` · токенов: 33

- `{{geo_insight_n.1..3}}` — 3 шт. (geo_insight_n.3 … geo_insight_n.2)
- `{{geo_n_amount}}`
- `{{geo_n_topgeo}}`
- `{{geo_n.1..8}}` — 8 шт. (geo_n.1 … geo_n.8)
- `{{geo_imp_n.1..8}}` — 8 шт. (geo_imp_n.1 … geo_imp_n.8)
- `{{geo_kpi_n.1..8}}` — 8 шт. (geo_kpi_n.1 … geo_kpi_n.8)
- `{{tactic n KPI type}}`
- `{{geo_n_topkpi}}`
- `{{tactic n}}`
- `{{geo_n_reco}}`

## Слайд 21 — МАСТЕР брейкдауна: Audience analysis
`tbl=False pics=4` · токенов: 25

- `{{aud_n_flag}}`
- `{{age_n_gr}}`
- `{{gender_n}}`
- `{{aud_n_takeaway}}`
- `{{aud_n_worked}}`
- `{{aud_n_1..5}}` — 5 шт. (aud_n_1 … aud_n_5)
- `{{aud_in_n_1..5}}` — 5 шт. (aud_in_n_1 … aud_in_n_5)
- `{{aud_n_reco}}`
- `{{tactic n male}}`
- `{{tactic n female}}`
- `{{tactic n}}`
- `{{age_n_<bucket>}}` — 6 шт. (age_n_18, 25, 35, 45, 55, 65)

## Слайд 22 — МАСТЕР брейкдауна: Device breakdown
`tbl=True pics=2` · токенов: 29

- `{{dev_n_ctr}}`
- `{{dev_n_vcr}}`
- `{{dev_n_amount}}`
- `{{dev_n_takeaway}}`
- `{{dev_n_worked}}`
- `{{dev_n_flag}}`
- `{{top_dev_n}}`
- `{{dev_proc_imps_n}}`
- `{{dev_n_reco}}`
- `{{mobile_imps_n}}`
- `{{mobile_ctr_n}}`
- `{{mobile_vcr_n}}`
- `{{mobile_spend_n}}`
- `{{ctv_imps_n}}`
- `{{ctv_vcr_n}}`
- `{{ctv_spend_n}}`
- `{{desktop_imps_n}}`
- `{{desktop_ctr_n}}`
- `{{desktop_vcr_n}}`
- `{{desktop_spend_n}}`
- `{{tablet_imps_n}}`
- `{{tablet_ctr_n}}`
- `{{tablet_vcr_n}}`
- `{{tablet_spend_n}}`
- `{{tactic n}}`
- `{{dev_n_mob}}`
- `{{dev_n_tv}}`
- `{{dev_n_desk}}`
- `{{dev_n_tab}}`

## Слайд 23 — МАСТЕР: Thoughts on tactic performance
`tbl=False pics=6` · токенов: 5

- `{{thoughts on tactic n performance 1..5}}` — 5 шт. (thoughts on tactic n performance 2 … thoughts on tactic n performance 4)
