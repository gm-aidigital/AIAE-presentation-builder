# Новый шаблон (15ryPctw…) — инвентарь id и env-блок

Источник: `presentations.get` дампа нового шаблона, 22 слайда, снят 2026-08-17.
Шаблон: `15ryPctwlmTy2uCpjrVjR1kVJErnpvQ9wXnF7IAka-OM`

Все id заведены в `application.yml` через env-override, дефолты остались от старого
шаблона (`11qzOC7…`), поэтому prod не меняется, пока переменные не выставлены.

## Порядок слайдов

| # | objectId | Что |
|---|---|---|
| 0 | `p1` | Титул |
| 1 | `p2` | Дивайдер Campaign Approach |
| 2 | `p3` | Media Plan Overview |
| 3 | `p4` | Strategic Framework |
| 4 | `p5` | Дивайдер Campaign Results (несёт `{{total imps}}` / `{{total ctr}}`) |
| 5 | `p6` | Awareness & market share (EOM-дроп) |
| 6–9 | `p7`, `g3f6fd96d9b2_1_139`, `_153`, `_167` | Platforms-группы 1–4 (таблицы `p7_i467`, `_143`, `_157`, `_171`) |
| **10** | **`p8`** | **МАСТЕР тактики** |
| 11 | `p15` | Thoughts on campaign performance |
| 12 | `p16` | Next steps |
| 13 | `p17` | Frequency & velocity (EOM-дроп, «DATA SIGNAL») |
| 14 | `p18` | Recommendations |
| 15 | `p19` | Финал |
| 16–20 | `p9`, `p10`, `p13`, `p11`, `p14` | Мастера брейкдаунов: tp, ca, geo, aud, dev |
| 21 | `g3f6fd96d9b2_1_0` | Мастер Thoughts on tactic performance |

Мастера (16–21) лежат после мастера тактики (10) — позиционирование копий корректно.

## Чарты на мастере тактики `p8`

| Тип | Элемент на слайде | Книга-источник | chartId в книге |
|---|---|---|---|
| Weighted contribution (dist) | `g3f73c15aec8_0_17` | `1fzG6Uuu2U1E3w0lnXGKO0TERYb59LkmSNSBgVR9DPtw` | `1431807138` |
| Daily pacing | `g3f73c15aec8_0_20` | `1LjtiI83T_0-v64CsoANTIjDzinipBk5ELe7NVzO5r2A` | `510717191` |
| Monthly impressions | `g3f73c15aec8_0_21` | `1r4aI3ToKfZ7W_TfVNokdShp91mXFIiA2eLy8PmTx4oI` | `510717191` |

Элементы на слайде нужны только для справки — в рантайме чарт на копии ищется по книге.

## env-блок для dev-деплоя

```
SLIDES_TEMPLATE_ID=15ryPctwlmTy2uCpjrVjR1kVJErnpvQ9wXnF7IAka-OM

TACTIC_MASTER_SLIDE_OBJECT_ID=p8

DAILY_CHART_TEMPLATE_SHEET_ID=1LjtiI83T_0-v64CsoANTIjDzinipBk5ELe7NVzO5r2A
MONTHLY_CHART_TEMPLATE_SHEET_ID=1r4aI3ToKfZ7W_TfVNokdShp91mXFIiA2eLy8PmTx4oI
DIST_CHART_TEMPLATE_SHEET_ID=1fzG6Uuu2U1E3w0lnXGKO0TERYb59LkmSNSBgVR9DPtw
DAILY_CHART_ID_IN_SHEET=510717191
MONTHLY_CHART_ID_IN_SHEET=510717191
DIST_CHART_ID_IN_SHEET=1431807138

BREAKDOWN_MASTER_SLIDE_TP=p9
BREAKDOWN_MASTER_SLIDE_CA=p10
BREAKDOWN_MASTER_SLIDE_GEO=p13
BREAKDOWN_MASTER_SLIDE_AUD=p11
BREAKDOWN_MASTER_SLIDE_DEV=p14
THOUGHTS_MASTER_SLIDE_OBJECT_ID=g3f6fd96d9b2_1_0

RESULTS_SLIDE_OBJECT_ID_1=p7
RESULTS_SLIDE_OBJECT_ID_2=g3f6fd96d9b2_1_139
RESULTS_SLIDE_OBJECT_ID_3=g3f6fd96d9b2_1_153
RESULTS_SLIDE_OBJECT_ID_4=g3f6fd96d9b2_1_167
SUMMARY_TABLE_OBJECT_ID_1=p7_i467
SUMMARY_TABLE_OBJECT_ID_2=g3f6fd96d9b2_1_143
SUMMARY_TABLE_OBJECT_ID_3=g3f6fd96d9b2_1_157
SUMMARY_TABLE_OBJECT_ID_4=g3f6fd96d9b2_1_171

EOM_DROP_SLIDE_OBJECT_IDS=p17,p6

BREAKDOWN_AUD_SOURCE_SHEET_ID=1K4XeQcIngAoYckwim7DWpA14pWC4ssuSypuUccA8kQc
BREAKDOWN_AUD_CHART_ID=522266257
BREAKDOWN_AUD_SEG_SOURCE_SHEET_ID=1PAurz6x7NC_35A1HKllbhSgfk5wvncN_oRnFraGy2r8
BREAKDOWN_AUD_SEG_CHART_ID=522266257
BREAKDOWN_DEV_SOURCE_SHEET_ID=1kDdh68zWxrujlW0dbhsNmLS5xu9jT3KbfjtbvbRBVvc
BREAKDOWN_DEV_CHART_ID=522266257
```

## Чарты на мастерах брейкдаунов

| Слайд | Элемент | Серия | Книга-источник | chartId |
|---|---|---|---|---|
| `p11` (aud) | `g3f73c15aec8_0_2` | `aud` — age distribution, импрешены по бакетам | `1K4XeQcIngAoYckwim7DWpA14pWC4ssuSypuUccA8kQc` | `522266257` |
| `p11` (aud) | `g3f73c15aec8_0_9` | `aud-seg` — top audience segments, affinity index | `1PAurz6x7NC_35A1HKllbhSgfk5wvncN_oRnFraGy2r8` | `522266257` |
| `p14` (dev) | `g3f73c15aec8_0_22` | `dev` — импрешены по девайсам | `1kDdh68zWxrujlW0dbhsNmLS5xu9jT3KbfjtbvbRBVvc` | `522266257` |

Чарт на копии ищется по книге, поэтому две серии на одном слайде обязаны смотреть в разные книги.
Для `aud-seg` названия сегментов пишутся в колонку A книги (у каждой кампании свои), значения —
в B, начиная с `BREAKDOWN_AUD_SEG_DATA_START_ROW` (по умолчанию 2, т.е. строка после шапки).

## Известные пробелы нового шаблона

1. Легенда Weighted Impression Contribution — сделано 2026-08-17: `{{tactic n contr}}` = доля
   импрешенов тактики от `{{total imps}}` в процентах, `{{tactic n other contr}}` = 100% минус она.
   Считается в коде из финальных значений (после наложения шита), ячейки в шите не нужны.
   **ТРЕБУЕТ ПРАВКИ ШАБЛОНА:** в легенде на мастере `p8` токен `{{other contr}}` надо переименовать
   в `{{tactic n other contr}}` — без `n` значение будет одинаковым на всех тактиках.
   `{{so what N}}` — сделано 2026-08-17: колонка «So what?» есть в шите (General!R), значение
   выбирается из фиксированного каталога `SoWhatPhrase` по funnel-цели тактики, читается обратно
   из шита на шаге деки (правка в шите побеждает).
2. ~~Мастер device без чарта, аудитория с двумя чартами~~ — сделано 2026-08-17: чарт на слайде
   ищется по книге-источнику, конфиг брейкдаун-чартов теперь по СЕРИЯМ (`aud` / `aud-seg` / `dev`),
   device-чарт добавлен в шаблон.
3. `{{proposal overview}}` остался одним токеном (в плане предполагалось разбиение на 4) —
   правок кода не требует.
